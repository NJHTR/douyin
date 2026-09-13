[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:9192',
    [string]$PeerBaseUrl = 'http://localhost:9193',
    [string]$EmailA = 'rtc-deploy-a@example.test',
    [string]$EmailB = 'rtc-deploy-b@example.test',
    [string]$Password = 'password',
    [int]$PageSize = 6,
    [switch]$RequireDifferentHome,
    [Alias('SeedRealBehavior', 'ExerciseRealBehavior')]
    [switch]$SeedBehaviorSignals,
    [ValidateRange(0, 60)]
    [int]$SignalWaitSeconds = 3,
    [switch]$SkipCrossInstance,
    [int]$TimeoutSeconds = 15
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Assert-Result([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Host "PASS: $Message" -ForegroundColor Green
}

function Invoke-Api([string]$Method, [string]$Path, [hashtable]$Headers = @{}, $Body = $null) {
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $Headers
        TimeoutSec = $TimeoutSeconds
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Compress)
    }
    Invoke-RestMethod @params
}

function Login([string]$Email) {
    $response = Invoke-Api 'Post' '/api/login' @{} @{ email = $Email; password = $Password }
    Assert-Result ($response.code -eq 200 -and $response.data.token) "$Email can authenticate"
    return @{
        Token = [string]$response.data.token
        UserId = [long]$response.data.user_id
    }
}

function Get-Feed([hashtable]$Identity, [string]$Mode, [string]$SessionId,
                  [int]$Start = 0, [string]$ApiUrl = $BaseUrl) {
    $headers = @{ Authorization = "Bearer $($Identity.Token)" }
    $path = "/api/video/recommended?start=$Start&pageSize=$PageSize&feedMode=$Mode&client_session_id=$SessionId"
    $previousBaseUrl = $script:BaseUrl
    try {
        $script:BaseUrl = $ApiUrl.TrimEnd('/')
        $response = Invoke-Api 'Get' $path $headers
    } finally {
        $script:BaseUrl = $previousBaseUrl
    }
    Assert-Result ($response.code -eq 200 -and $null -ne $response.data) "$Mode response is successful"
    return $response.data
}

function Get-VideoIds($Page) {
    @($Page.list | ForEach-Object {
        if ($_.aweme_id) { [string]$_.aweme_id }
        elseif ($_.id) { [string]$_.id }
    } | Where-Object { $_ })
}

function Get-AuthorIds($Page) {
    @($Page.list | ForEach-Object {
        if ($_.author -and $_.author.uid) { [long]$_.author.uid }
    })
}

function Get-DurationMilliseconds($Item) {
    if ($null -eq $Item -or $null -eq $Item.duration) { return $null }
    try {
        $value = [double]$Item.duration
        if ([double]::IsNaN($value) -or [double]::IsInfinity($value) -or $value -lt 0) {
            return $null
        }
        return $value
    } catch {
        return $null
    }
}

function Get-DurationSeconds($Item) {
    $durationMs = Get-DurationMilliseconds $Item
    if ($null -eq $durationMs -or $durationMs -le 0) { return $null }
    return [math]::Max(0.001, [math]::Min(86400.0, $durationMs / 1000.0))
}

function Record-Watch([hashtable]$Identity, [string]$VideoId, [hashtable]$Body) {
    $headers = @{ Authorization = "Bearer $($Identity.Token)" }
    $response = Invoke-Api 'Post' "/api/video/watch/$VideoId" $headers $Body
    Assert-Result ($response.code -eq 200) "watch signal accepted for video $VideoId"
}

function Seed-RealBehaviorSignals([hashtable]$IdentityA, $PageA,
                                  [hashtable]$IdentityB, $PageB) {
    $itemA = @($PageA.list | Where-Object {
        $durationMs = Get-DurationMilliseconds $_
        $null -ne $durationMs -and $durationMs -gt 0
    } | Select-Object -First 1)[0]
    $videoA = @(Get-VideoIds @{ list = @($itemA) })[0]
    $itemB = @($PageB.list | Where-Object {
        $candidateId = @(Get-VideoIds @{ list = @($_) })[0]
        $durationMs = Get-DurationMilliseconds $_
        $candidateId -and $candidateId -ne $videoA -and
            $null -ne $durationMs -and $durationMs -gt 0
    } | Select-Object -First 1)[0]
    if ($null -eq $itemB) {
        $itemB = @($PageB.list | Where-Object {
            $durationMs = Get-DurationMilliseconds $_
            $null -ne $durationMs -and $durationMs -gt 0
        } | Select-Object -First 1)[0]
    }
    $videoB = @(Get-VideoIds @{ list = @($itemB) })[0]
    Assert-Result ($videoA -and $videoB) 'feed returned measurable videos to seed real behavior signals'

    $durationA = Get-DurationSeconds $itemA
    $durationB = Get-DurationSeconds $itemB
    $quickSkipSeconds = [math]::Min(1.0, [math]::Max(0.001, $durationB * 0.1))
    $sessionA = "accept-signal-a-$([guid]::NewGuid().ToString('N'))"
    $sessionB = "accept-signal-b-$([guid]::NewGuid().ToString('N'))"

    # A natural completion and B an intentional quick skip are both valid
    # client events. They are derived from the returned video IDs and media
    # metadata; no account ID/hash/random ordering input is introduced.
    Record-Watch $IdentityA $videoA @{
        watch_duration = $durationA
        video_duration = $durationA
        finished = $true
        profile_sample = $true
        session_id = $sessionA
        swipe_seconds = $durationA
        last_position = $durationA
        traffic_source = 'ACCEPTANCE_SIGNAL_A'
    }
    Record-Watch $IdentityB $videoB @{
        watch_duration = $quickSkipSeconds
        video_duration = $durationB
        finished = $false
        profile_sample = $true
        session_id = $sessionB
        swipe_seconds = $quickSkipSeconds
        last_position = $quickSkipSeconds
        traffic_source = 'ACCEPTANCE_SIGNAL_B'
    }

    if ($SignalWaitSeconds -gt 0) {
        Write-Host "Waiting $SignalWaitSeconds second(s) for asynchronous profile updates" -ForegroundColor DarkGray
        Start-Sleep -Seconds $SignalWaitSeconds
    }
}

function Compare-Ids([string[]]$Left, [string[]]$Right) {
    return (($Left -join ',') -eq ($Right -join ','))
}

$identityA = Login $EmailA
$identityB = Login $EmailB
$sessionA = "accept-home-$([guid]::NewGuid().ToString('N'))"
$sessionB = "accept-home-$([guid]::NewGuid().ToString('N'))"

# A repeated request with the same browser session must reuse the same pool.
$homeA1 = Get-Feed $identityA 'HOME' $sessionA 0
$homeA2 = Get-Feed $identityA 'HOME' $sessionA 0
$homeAIds1 = Get-VideoIds $homeA1
$homeAIds2 = Get-VideoIds $homeA2
Assert-Result ($homeAIds1.Count -gt 0) 'HOME returns videos for account A'
Assert-Result (Compare-Ids $homeAIds1 $homeAIds2) 'same account/session returns a stable HOME page'

$homeB = Get-Feed $identityB 'HOME' $sessionB 0
$homeBIds = Get-VideoIds $homeB
Assert-Result ($homeBIds.Count -gt 0) 'HOME returns videos for account B'
if (Compare-Ids $homeAIds1 $homeBIds) {
    if ($RequireDifferentHome -and -not $SeedBehaviorSignals) {
        throw 'FAIL: A/B HOME pages are identical despite the test declaring that distinct profile/behavior signals were seeded.'
    }
    Write-Warning 'A/B HOME pages are identical. This is valid when their profile, behavior and social evidence are equivalent; account ID alone must not force a different order.'
} else {
    Write-Host 'OBSERVED: A/B HOME pages differ because their available recommendation signals differ' -ForegroundColor Cyan
}

if ($SeedBehaviorSignals) {
    Seed-RealBehaviorSignals $identityA $homeA1 $identityB $homeB
    $seededHomeA = Get-Feed $identityA 'HOME' "accept-seeded-home-$([guid]::NewGuid().ToString('N'))" 0
    $seededHomeB = Get-Feed $identityB 'HOME' "accept-seeded-home-$([guid]::NewGuid().ToString('N'))" 0
    $seededAIds = Get-VideoIds $seededHomeA
    $seededBIds = Get-VideoIds $seededHomeB
    Assert-Result ($seededAIds.Count -gt 0 -and $seededBIds.Count -gt 0) 'HOME returns videos after real behavior signals'
    if (Compare-Ids $seededAIds $seededBIds) {
        if ($RequireDifferentHome) {
            throw 'FAIL: A/B HOME pages stayed identical after real completion/quick-skip signals. Add enough approved content/features or inspect asynchronous profile processing; account ID alone must not force a difference.'
        }
        Write-Warning 'A/B HOME pages stayed identical after the real behavior exercise. This is acceptable when the available content/features cannot produce a measurable ranking change.'
    } else {
        Write-Host 'OBSERVED: A/B HOME pages differ after real completion/quick-skip signals' -ForegroundColor Cyan
    }
}

# The candidate pool is shared by all stateless API replicas.  This catches a
# Redis serializer/configuration regression that a single-port smoke test
# cannot see: the same account/session must observe the same pool regardless
# of which replica handles the request, and the next page must advance within
# that pool without repeating the first page.
if (-not $SkipCrossInstance) {
    Assert-Result ($PeerBaseUrl -and $PeerBaseUrl.Trim()) 'peer API URL is configured for cross-instance verification'
    $crossSession = "accept-cross-$([guid]::NewGuid().ToString('N'))"
    $crossBase = Get-Feed $identityA 'HOME' $crossSession 0 $BaseUrl
    $crossPeer = Get-Feed $identityA 'HOME' $crossSession 0 $PeerBaseUrl
    $crossNext = Get-Feed $identityA 'HOME' $crossSession $PageSize $PeerBaseUrl
    $crossBaseIds = Get-VideoIds $crossBase
    $crossPeerIds = Get-VideoIds $crossPeer
    $crossNextIds = Get-VideoIds $crossNext
    Assert-Result (Compare-Ids $crossBaseIds $crossPeerIds) 'same account/session is stable across API replicas'
    Assert-Result (@($crossBaseIds | Where-Object { $_ -in $crossNextIds }).Count -eq 0) 'cross-replica next page does not repeat the first page'
}

# HOT must be a valid global feed and must not require a profile signal.
$hotA = Get-Feed $identityA 'HOT' "accept-hot-$([guid]::NewGuid().ToString('N'))" 0
$hotB = Get-Feed $identityB 'HOT' "accept-hot-$([guid]::NewGuid().ToString('N'))" 0
Assert-Result ((Get-VideoIds $hotA).Count -gt 0) 'HOT returns videos'
Assert-Result ((Get-VideoIds $hotB).Count -gt 0) 'HOT returns videos for account B'

# Social channels must not return authors outside the viewer's graph. The
# disposable seed establishes A<->B and A<->C relationships.
$followingA = Get-Feed $identityA 'FOLLOWING' "accept-following-$([guid]::NewGuid().ToString('N'))" 0
$followingAuthors = Get-AuthorIds $followingA
Assert-Result (@($followingAuthors | Where-Object { $_ -notin @(991002, 991003) }).Count -eq 0) 'FOLLOWING contains only A followed authors'

$friendsA = Get-Feed $identityA 'FRIENDS' "accept-friends-$([guid]::NewGuid().ToString('N'))" 0
$friendAuthors = Get-AuthorIds $friendsA
Assert-Result (@($friendAuthors | Where-Object { $_ -notin @(991002, 991003) }).Count -eq 0) 'FRIENDS contains only A mutual-follow authors'

$longA = Get-Feed $identityA 'LONG_VIDEO' "accept-long-$([guid]::NewGuid().ToString('N'))" 0
Assert-Result (@($longA.list).Count -gt 0) 'LONG_VIDEO returns at least one video'
$invalidLongVideos = @($longA.list | ForEach-Object {
    $durationMs = Get-DurationMilliseconds $_
    if ($null -eq $durationMs -or $durationMs -lt 60000) { $_ }
})
Assert-Result ($invalidLongVideos.Count -eq 0) 'LONG_VIDEO returns only videos at least 60 seconds (top-level duration is milliseconds)'

Write-Host "Recommendation acceptance completed against $BaseUrl" -ForegroundColor Cyan
