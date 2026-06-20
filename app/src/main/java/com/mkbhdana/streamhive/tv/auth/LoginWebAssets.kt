package com.mkbhdana.streamhive.tv.auth

/**
 * The self-contained HTML login page served by [TvAuthServer] to the user's
 * phone. It mirrors the two flows of the mobile `AuthScreen` (OAuth 2.0 and
 * Service Account) and POSTs the entered credentials back to the TV.
 *
 * Written without `$` so it is safe inside a Kotlin raw string; the JS uses
 * plain string concatenation instead of template literals.
 */
object LoginWebAssets {
    val PAGE: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>StreamHive TV Sign-in</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family:-apple-system,Segoe UI,Roboto,sans-serif; background:#0d0d1a; color:#e8e8f0; padding:20px; }
  .card { max-width:560px; margin:0 auto; background:#1a1a2e; border-radius:16px; padding:20px; }
  h1 { font-size:22px; margin:0 0 4px; }
  p.sub { color:#a0a0b8; margin:0 0 18px; font-size:14px; }
  .tabs { display:flex; gap:8px; margin-bottom:16px; }
  .tab { flex:1; padding:12px; text-align:center; border-radius:10px; background:#16213e; color:#a0a0b8; cursor:pointer; font-weight:600; }
  .tab.active { background:#7c4dff; color:#fff; }
  label { display:block; font-size:13px; color:#a0a0b8; margin:12px 0 6px; }
  input, textarea { width:100%; padding:12px; border-radius:10px; border:1px solid #2a2a44; background:#0d0d1a; color:#e8e8f0; font-size:15px; }
  textarea { min-height:140px; font-family:monospace; }
  button { width:100%; margin-top:16px; padding:14px; border:none; border-radius:10px; background:#7c4dff; color:#fff; font-size:16px; font-weight:600; cursor:pointer; }
  button.secondary { background:#16213e; }
  .hidden { display:none; }
  .status { margin-top:16px; padding:12px; border-radius:10px; font-size:14px; }
  .status.ok { background:rgba(105,240,174,0.15); color:#69f0ae; }
  .status.err { background:rgba(255,82,82,0.15); color:#ff5252; }
  a { color:#00e5ff; word-break:break-all; }
</style>
</head>
<body>
<div class="card">
  <h1>StreamHive</h1>
  <p class="sub">Sign in on your phone to connect your Android TV.</p>
  <div class="tabs">
    <div id="tab-oauth" class="tab active" onclick="showTab('oauth')">OAuth 2.0</div>
    <div id="tab-sa" class="tab" onclick="showTab('sa')">Service Account</div>
  </div>

  <div id="pane-oauth">
    <label>Client ID</label>
    <input id="clientId" autocomplete="off"/>
    <label>Client Secret</label>
    <input id="clientSecret" type="password" autocomplete="off"/>
    <label>Redirect URI</label>
    <input id="redirectUri" autocomplete="off" placeholder="as configured in Google Cloud"/>
    <button onclick="genUrl()">Step 1: Generate Authorization URL</button>
    <div id="authUrlBox" class="hidden">
      <label>Step 2: open this URL, approve, then copy the code or redirected URL</label>
      <a id="authUrl" target="_blank"></a>
      <label>Step 3: paste authorization code (or full redirected URL)</label>
      <input id="authCode" autocomplete="off"/>
      <button onclick="complete()">Connect</button>
    </div>
  </div>

  <div id="pane-sa" class="hidden">
    <label>Service account JSON</label>
    <textarea id="saJson" placeholder='{ "type": "service_account", ... }'></textarea>
    <input id="saFile" type="file" accept="application/json" onchange="loadFile(event)" style="margin-top:10px"/>
    <button onclick="authSa()">Authenticate</button>
  </div>

  <div id="status" class="status hidden"></div>
</div>

<script>
  function showTab(which){
    document.getElementById('tab-oauth').classList.toggle('active', which==='oauth');
    document.getElementById('tab-sa').classList.toggle('active', which==='sa');
    document.getElementById('pane-oauth').classList.toggle('hidden', which!=='oauth');
    document.getElementById('pane-sa').classList.toggle('hidden', which!=='sa');
  }
  function val(id){ return document.getElementById(id).value.trim(); }
  function setStatus(ok, msg){
    var s = document.getElementById('status');
    s.className = 'status ' + (ok ? 'ok' : 'err');
    s.textContent = msg;
  }
  function post(path, body){
    return fetch(path, { method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(body) })
      .then(function(r){ return r.json(); });
  }
  function genUrl(){
    post('/oauth/url', { clientId: val('clientId'), clientSecret: val('clientSecret'), redirectUri: val('redirectUri') })
      .then(function(res){
        if(res.url){
          var a = document.getElementById('authUrl');
          a.href = res.url; a.textContent = res.url;
          document.getElementById('authUrlBox').classList.remove('hidden');
          window.open(res.url, '_blank');
        } else { setStatus(false, res.message || 'Could not build URL'); }
      }).catch(function(){ setStatus(false, 'Network error'); });
  }
  function complete(){
    setStatus(true, 'Connecting...');
    post('/oauth/complete', {
      clientId: val('clientId'), clientSecret: val('clientSecret'),
      redirectUri: val('redirectUri'), authorizationCode: val('authCode')
    }).then(function(res){
      setStatus(res.ok, res.ok ? 'Signed in on your TV. You can close this page.' : (res.message || 'Authentication failed'));
    }).catch(function(){ setStatus(false, 'Network error'); });
  }
  function loadFile(e){
    var f = e.target.files[0]; if(!f) return;
    var reader = new FileReader();
    reader.onload = function(){ document.getElementById('saJson').value = reader.result; };
    reader.readAsText(f);
  }
  function authSa(){
    setStatus(true, 'Authenticating...');
    post('/service-account', { json: val('saJson') }).then(function(res){
      setStatus(res.ok, res.ok ? 'Signed in on your TV. You can close this page.' : (res.message || 'Authentication failed'));
    }).catch(function(){ setStatus(false, 'Network error'); });
  }
</script>
</body>
</html>
""".trimIndent()
}
