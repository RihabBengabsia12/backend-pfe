param(
    [string]$OutputPath = "diagramme-cartographie-exhaustive-backend.xml"
)

$ErrorActionPreference = 'Stop'

function Escape-Xml([string]$Value) {
    return [System.Security.SecurityElement]::Escape($Value)
}

function Add-Cell([System.Text.StringBuilder]$Builder, [string]$Id, [string]$Value, [string]$Style, [int]$X, [int]$Y, [int]$Width, [int]$Height, [string]$Parent = '1') {
    $safe = Escape-Xml $Value
    [void]$Builder.AppendLine("    <mxCell id=""$Id"" value=""$safe"" style=""$Style"" vertex=""1"" parent=""$Parent""><mxGeometry x=""$X"" y=""$Y"" width=""$Width"" height=""$Height"" as=""geometry""/></mxCell>")
}

$root = Split-Path -Parent $PSScriptRoot
$services = @('auth-service', 'admin-service', 'project-service', 'analyste-service', 'api-gateway', 'discovery-server')
$itemsByService = @{}

foreach ($service in $services) {
    $base = Join-Path $root "$service\src\main\java"
    if (-not (Test-Path $base)) { continue }
    $items = Get-ChildItem -Path $base -Recurse -Filter '*.java' | ForEach-Object {
        $relative = $_.FullName.Substring($base.Length).TrimStart('\')
        $parts = $relative -split '\\'
        $category = if ($parts.Count -le 3) { 'application' } else { $parts[$parts.Count - 2] }
        [PSCustomObject]@{ Category = $category; ClassName = $_.BaseName }
    }
    $itemsByService[$service] = $items
}

$builder = [System.Text.StringBuilder]::new()
[void]$builder.AppendLine('<mxGraphModel dx="2600" dy="1650" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1684" pageHeight="1191" math="0" shadow="0">')
[void]$builder.AppendLine('  <root>')
[void]$builder.AppendLine('    <mxCell id="0"/>')
[void]$builder.AppendLine('    <mxCell id="1" parent="0"/>')
Add-Cell $builder 'title' 'Cartographie exhaustive des classes Java du backend ProjectIQ' 'text;html=1;align=center;verticalAlign=middle;fontStyle=1;fontSize=20;' 600 20 1400 35
Add-Cell $builder 'subtitle' 'Document annexe - classes regroupees par microservice et package. Les dependances detaillees sont presentees dans les diagrammes de conception et de sequence.' 'text;html=1;align=center;verticalAlign=middle;fontSize=11;' 400 58 1800 25
Add-Cell $builder 'legend' 'Cette figure inventorie les 218 fichiers Java du code principal. Elle est prevue pour une annexe en format A3 paysage.' 'shape=note;whiteSpace=wrap;html=1;strokeWidth=2;fillColor=#ffffff;fontSize=10;' 35 95 480 45

$columnDefinitions = @(
    @{ Name = 'auth-service'; X = 30; Width = 405; Title = 'auth-service - securite' },
    @{ Name = 'admin-service'; X = 455; Width = 405; Title = 'admin-service - administration' },
    @{ Name = 'project-service'; X = 880; Width = 620; Title = 'project-service - dossiers et validation' },
    @{ Name = 'analyste-service'; X = 1520; Width = 1040; Title = 'analyste-service - analyse et livrables' }
)

$cellNumber = 1
foreach ($definition in $columnDefinitions) {
    $service = $definition.Name
    $items = $itemsByService[$service]
    $groups = $items | Group-Object Category | Sort-Object Name
    $x = [int]$definition.X
    $width = [int]$definition.Width
    $packageId = "pkg$cellNumber"
    $cellNumber++
    Add-Cell $builder $packageId $definition.Title 'swimlane;html=1;startSize=30;horizontal=1;strokeWidth=2;fontStyle=1;fontSize=14;fillColor=#ffffff;' $x 160 $width 1335

    $innerX1 = $x + 20
    $innerX2 = $x + [int]($width / 2) + 5
    $innerWidth = [int](($width - 55) / 2)
    $columns = @(@(), @())
    $heights = @(0, 0)
    foreach ($group in $groups) {
        $target = if ($heights[0] -le $heights[1]) { 0 } else { 1 }
        $columns[$target] += ,$group
        $heights[$target] += 34 + ($group.Count * 12)
    }
    for ($col = 0; $col -lt 2; $col++) {
        $cursorY = 205
        $cursorX = if ($col -eq 0) { $innerX1 } else { $innerX2 }
        foreach ($group in $columns[$col]) {
            $names = ($group.Group | Sort-Object ClassName | ForEach-Object { $_.ClassName }) -join "`n"
            $height = [Math]::Max(58, 30 + ($group.Count * 12))
            $id = "cls$cellNumber"
            $cellNumber++
            $label = "<<$($group.Name)>>`n$names"
            Add-Cell $builder $id $label 'swimlane;html=1;startSize=22;horizontal=1;strokeWidth=1.2;fontSize=8;fillColor=#ffffff;align=left;spacingLeft=5;' $cursorX $cursorY $innerWidth $height $packageId
            $cursorY += $height + 14
        }
    }
}

$infraY = 1525
Add-Cell $builder 'gateway' 'api-gateway`nApiGatewayApplication' 'swimlane;html=1;startSize=22;horizontal=1;strokeWidth=2;fontSize=10;fillColor=#ffffff;' 35 $infraY 310 58
Add-Cell $builder 'discovery' 'discovery-server`nDiscoveryServerApplication' 'swimlane;html=1;startSize=22;horizontal=1;strokeWidth=2;fontSize=10;fillColor=#ffffff;' 375 $infraY 310 58
Add-Cell $builder 'external' 'Composants externes relies au backend`nRabbitMQ | MinIO | PostgreSQL | ia-service / Claude | Mailpit' 'swimlane;html=1;startSize=22;horizontal=1;strokeWidth=2;fontSize=10;fillColor=#ffffff;' 720 $infraY 750 58
Add-Cell $builder 'note' 'Les dependances entre Controller, Service et Repository sont volontairement detaillees dans le diagramme applicatif global. Les relations entre entites sont detaillees dans les diagrammes des entites.' 'shape=note;whiteSpace=wrap;html=1;strokeWidth=2;fillColor=#ffffff;fontSize=10;' 1510 $infraY 850 58

[void]$builder.AppendLine('  </root>')
[void]$builder.AppendLine('</mxGraphModel>')

$output = Join-Path $root $OutputPath
[System.IO.File]::WriteAllText($output, $builder.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Output $output
