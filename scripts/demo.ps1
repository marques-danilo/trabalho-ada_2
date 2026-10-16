# Roteiro de demonstracao do projeto Flash Sale (versao PowerShell).
#
# Equivalente a scripts/demo.sh, para quem apresenta a partir do Windows.
# Pre-requisito: a stack precisa estar no ar.
#   wsl -e bash -lc 'cd /mnt/c/Users/caiob/Projects/flash-sale-checkout && docker compose up -d'
#
# Uso: .\scripts\demo.ps1

$ErrorActionPreference = 'Stop'

$Checkout  = if ($env:CHECKOUT_URL)  { $env:CHECKOUT_URL }  else { 'http://localhost:8081' }
$Processor = if ($env:PROCESSOR_URL) { $env:PROCESSOR_URL } else { 'http://localhost:8082' }
$Cpf       = '50329291076'
$Nome      = 'Fernando Augusto'
$Espera    = 2

function Titulo($texto) { Write-Host "`n== $texto" -ForegroundColor Cyan }
function Info($texto)   { Write-Host $texto -ForegroundColor DarkGray }
function Json($objeto)  { $objeto | ConvertTo-Json -Compress -Depth 5 }

function New-Pedido($produtoId, $quantidade) {
    $corpo = @{
        produto_id   = $produtoId
        quantidade   = $quantidade
        cliente_cpf  = $Cpf
        cliente_nome = $Nome
    } | ConvertTo-Json

    Invoke-RestMethod -Method Post -Uri "$Checkout/v1/orders" `
        -ContentType 'application/json' -Body $corpo
}

# Executa a chamada e devolve o status HTTP junto do corpo, inclusive em respostas de erro.
function Invoke-Api($metodo, $uri, $corpo) {
    try {
        $params = @{ Method = $metodo; Uri = $uri; SkipHttpErrorCheck = $true }
        if ($corpo) {
            $params.ContentType = 'application/json'
            $params.Body = $corpo
        }
        $resposta = Invoke-WebRequest @params
        return [pscustomobject]@{ Status = $resposta.StatusCode; Corpo = $resposta.Content }
    } catch {
        return [pscustomobject]@{ Status = 'erro'; Corpo = $_.Exception.Message }
    }
}

Titulo '0. Aguardando checkout-service e order-processor-service'
$pronto = $false
foreach ($i in 1..60) {
    try {
        Invoke-RestMethod "$Checkout/actuator/health"  -TimeoutSec 3 | Out-Null
        Invoke-RestMethod "$Processor/actuator/health" -TimeoutSec 3 | Out-Null
        $pronto = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $pronto) {
    Write-Error 'Servicos nao subiram em tempo. Verifique: wsl -e bash -lc "cd <projeto> && docker compose ps"'
}
Info 'Ambos os servicos responderam.'

Titulo '1. Estoque inicial do produto'
Json (Invoke-RestMethod "$Checkout/v1/stock/PROD-001")
Info 'O estoque e carregado no Redis na inicializacao do checkout-service.'

Titulo '2-3. POST /v1/orders -> 202 Accepted'
$pedidoOk = New-Pedido 'PROD-001' 2
Json $pedidoOk
Info "pedido_id = $($pedidoOk.pedido_id) (status inicial PROCESSING)"

Titulo '4. Estoque reservado atomicamente no Redis'
Json (Invoke-RestMethod "$Checkout/v1/stock/PROD-001")
Info 'O saldo caiu em 2 unidades no momento da requisicao.'

Info 'Aguardando o consumo da fila order-created...'
Start-Sleep -Seconds $Espera

Titulo '5-6. Pedido persistido pelo order-processor-service com status CONFIRMED'
Json (Invoke-RestMethod "$Processor/internal/orders/$($pedidoOk.pedido_id)")
Info 'Esta e a base propria do Order Processor.'

Titulo '7-8. GET /v1/orders/{pedido_id} no checkout (Cache-Aside no Redis)'
Json (Invoke-RestMethod "$Checkout/v1/orders/$($pedidoOk.pedido_id)")
Info 'Primeira leitura vem do cache; em caso de miss, o checkout consulta sua propria base.'

Titulo 'Retry: pedido inexistente faz 3 tentativas e devolve 404'
$cronometro = [System.Diagnostics.Stopwatch]::StartNew()
$r404 = Invoke-Api 'GET' "$Checkout/v1/orders/00000000-0000-4000-8000-000000000000" $null
$cronometro.Stop()
Write-Host "HTTP $($r404.Status) em $([math]::Round($cronometro.Elapsed.TotalSeconds, 3))s"
Write-Host $r404.Corpo
Info 'O tempo total reflete as 3 tentativas com o intervalo configurado.'

Titulo 'Estoque insuficiente -> 409 Conflict'
$corpo409 = @{ produto_id = 'PROD-001'; quantidade = 999999; cliente_cpf = $Cpf; cliente_nome = $Nome } | ConvertTo-Json
$r409 = Invoke-Api 'POST' "$Checkout/v1/orders" $corpo409
Write-Host "HTTP $($r409.Status)"
Write-Host $r409.Corpo

Titulo 'Validacao de entrada -> 400 Bad Request'
$corpo400 = @{ produto_id = 'PROD-001'; quantidade = 0; cliente_cpf = $Cpf } | ConvertTo-Json
$r400 = Invoke-Api 'POST' "$Checkout/v1/orders" $corpo400
Write-Host "HTTP $($r400.Status)"
Write-Host $r400.Corpo

Titulo '9. Cenario de falha: PROD-FAIL sempre falha no processamento'
Write-Host "Estoque antes: $(Json (Invoke-RestMethod "$Checkout/v1/stock/PROD-FAIL"))"
$pedidoFalha = New-Pedido 'PROD-FAIL' 3
Json $pedidoFalha
Info "pedido_id = $($pedidoFalha.pedido_id)"
Json (Invoke-RestMethod "$Checkout/v1/stock/PROD-FAIL")
Info 'Estoque ja reservado (baixou 3 unidades).'

Info 'Aguardando order-created -> falha -> order-compensation -> devolucao do estoque...'
Start-Sleep -Seconds $Espera

Titulo '10-11. Compensacao consumida e estoque devolvido ao Redis'
Json (Invoke-RestMethod "$Checkout/v1/stock/PROD-FAIL")
Info 'O saldo voltou ao valor anterior: a acao compensatoria da SAGA foi aplicada.'

Titulo '12. Pedido atualizado para CANCELLED'
Write-Host 'Base do Order Processor:'
Json (Invoke-RestMethod "$Processor/internal/orders/$($pedidoFalha.pedido_id)")
Write-Host 'Consulta publica no Checkout:'
Json (Invoke-RestMethod "$Checkout/v1/orders/$($pedidoFalha.pedido_id)")

Titulo 'Demonstracao concluida'
Info 'Painel do RabbitMQ: http://localhost:15672 (guest/guest)'
