package com.mkbhdana.streamhive.tv.manage

/**
 * Self-contained HTML page served by [TvManageServer]. Lets the phone set the
 * TMDB key, browse drives (structured rows + breadcrumb) to add/remove catalog
 * folders, and import/export a settings backup. Written without `$` so it is
 * safe inside a Kotlin raw string.
 */
object ManageWebAssets {
    val PAGE: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>StreamHive Manage</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family:-apple-system,Segoe UI,Roboto,sans-serif; background:#0b0b0d; color:#f3f3f5; padding:18px; }
  .card { max-width:640px; margin:0 auto; background:#141416; border-radius:16px; padding:18px; }
  h1 { font-size:21px; margin:0 0 14px; }
  .tabs { display:flex; gap:8px; margin-bottom:16px; }
  .tab { flex:1; padding:11px; text-align:center; border-radius:10px; background:#1b1b1f; color:#9c9ca4; cursor:pointer; font-weight:600; }
  .tab.active { background:#ffffff; color:#0b0b0d; }
  label { display:block; font-size:13px; color:#9c9ca4; margin:14px 0 6px; }
  input { width:100%; padding:12px; border-radius:10px; border:1px solid #2c2c32; background:#0b0b0d; color:#f3f3f5; font-size:15px; }
  button { margin-top:12px; padding:12px 16px; border:none; border-radius:10px; background:#ffffff; color:#0b0b0d; font-size:15px; font-weight:600; cursor:pointer; }
  button.sec { background:#26262b; color:#f3f3f5; }
  button.danger { background:#3a1f22; color:#ff6b6b; }
  .hidden { display:none; }
  .row { display:flex; align-items:center; justify-content:space-between; padding:10px 12px; background:#1b1b1f; border-radius:10px; margin-bottom:8px; }
  .row .meta { color:#9c9ca4; font-size:12px; }
  .folder-item.dragging { opacity:0.5; }
  .handle { cursor:grab; padding:2px 12px 2px 2px; color:#9c9ca4; font-size:20px; touch-action:none; }
  #browseList { max-height:40vh; overflow-y:auto; margin-bottom:8px; }
  .crumbs { font-size:14px; color:#9c9ca4; margin:6px 0 10px; line-height:1.8; }
  .crumb { cursor:pointer; color:#e7e7ea; padding:3px 8px; background:#1b1b1f; border-radius:8px; }
  .folder-row { padding:13px 14px; background:#1b1b1f; border-radius:10px; margin-bottom:6px; cursor:pointer; display:flex; align-items:center; gap:10px; }
  .folder-row:hover { background:#26262b; }
  .meta { color:#9c9ca4; font-size:13px; }
  .status { margin-top:14px; padding:10px; border-radius:10px; font-size:14px; }
  .status.ok { background:rgba(105,240,174,0.15); color:#69f0ae; }
  .status.err { background:rgba(255,82,82,0.15); color:#ff5252; }
  a.btn { display:inline-block; margin-top:12px; padding:12px 16px; border-radius:10px; background:#ffffff; color:#0b0b0d; font-weight:600; text-decoration:none; }
</style>
</head>
<body>
<div class="card">
  <h1>StreamHive</h1>
  <div class="tabs">
    <div id="t-key" class="tab active" onclick="showTab('key')">TMDB Key</div>
    <div id="t-folders" class="tab" onclick="showTab('folders')">Folders</div>
    <div id="t-backup" class="tab" onclick="showTab('backup')">Backup</div>
  </div>

  <div id="p-key">
    <label>TMDB API Key</label>
    <input id="key" autocomplete="off"/>
    <button onclick="saveKey()">Save Key</button>
  </div>

  <div id="p-folders" class="hidden">
    <label>Catalog folders</label>
    <div id="folderList"></div>
    <label>Add a folder</label>
    <div id="crumbs" class="crumbs"></div>
    <div id="browseList"></div>
    <div id="addRow" class="hidden">
      <button onclick="addCurrent('movie')">Add this folder as Movies</button>
      <button class="sec" onclick="addCurrent('tv')">Add as Series</button>
    </div>
  </div>

  <div id="p-backup" class="hidden">
    <label>Download a full backup (settings, folders, watch history, metadata)</label>
    <a class="btn" href="/export" download>Download Backup</a>
    <label>Restore from a backup file</label>
    <input id="importFile" type="file" accept="application/json" onchange="loadImport(event)"/>
    <button onclick="doImport()">Restore</button>
  </div>

  <div id="status" class="status hidden"></div>
</div>

<script>
  var drives = [];
  var path = [];      // [{driveId, folderId, name}] — first entry is the drive
  var importJson = "";

  function showTab(w){
    ['key','folders','backup'].forEach(function(n){
      document.getElementById('t-'+n).classList.toggle('active', n===w);
      document.getElementById('p-'+n).classList.toggle('hidden', n!==w);
    });
  }
  function setStatus(ok, msg){
    var s=document.getElementById('status'); s.className='status '+(ok?'ok':'err'); s.textContent=msg;
  }
  function postJson(p, b){
    return fetch(p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)}).then(function(r){return r.json();});
  }
  function esc(s){ return (s||'').split("'").join("\\'"); }
  function refresh(){
    fetch('/state').then(function(r){return r.json();}).then(function(s){
      document.getElementById('key').value = s.tmdbKey || '';
      drives = s.drives || [];
      renderFolders(s.folders||[]);
      renderRoot();
    });
  }
  function renderFolders(folders){
    var html='';
    if(folders.length===0){ html='<div class="meta">No folders yet.</div>'; }
    if(folders.length>1){ html='<div class="meta">Drag the ≡ handle to reorder.</div>'; }
    folders.forEach(function(f){
      html += '<div class="row folder-item" data-id="'+f.id+'">'+
              '<span class="handle" onpointerdown="dragStart(event)" onpointermove="dragMove(event)" onpointerup="dragEnd(event)">&#8801;</span>'+
              '<div style="flex:1">'+f.name+' <span class="meta">('+(f.type==='tv'?'Series':'Movies')+')</span></div>'+
              '<button class="danger" onclick="removeFolder(\''+f.id+'\')">Remove</button></div>';
    });
    document.getElementById('folderList').innerHTML = html;
  }

  // ── Drag-to-reorder (mouse + touch via pointer events) ──
  var dragRow = null;
  function dragStart(e){
    dragRow = e.target.closest('.folder-item');
    if(!dragRow) return;
    dragRow.classList.add('dragging');
    e.target.setPointerCapture(e.pointerId);
  }
  function dragMove(e){
    if(!dragRow) return;
    var list=document.getElementById('folderList');
    var rows=Array.prototype.slice.call(list.querySelectorAll('.folder-item:not(.dragging)'));
    var after=null;
    for(var i=0;i<rows.length;i++){ var b=rows[i].getBoundingClientRect(); if(e.clientY < b.top + b.height/2){ after=rows[i]; break; } }
    if(after) list.insertBefore(dragRow, after); else list.appendChild(dragRow);
  }
  function dragEnd(e){
    if(!dragRow) return;
    dragRow.classList.remove('dragging'); dragRow=null;
    commitOrder();
  }
  function commitOrder(){
    var list=document.getElementById('folderList');
    var ids=Array.prototype.slice.call(list.querySelectorAll('.folder-item')).map(function(r){ return r.getAttribute('data-id'); });
    postJson('/folder/order',{ids:ids}).then(function(){ setStatus(true,'Order saved.'); });
  }
  function saveKey(){
    postJson('/tmdb-key',{key:document.getElementById('key').value.trim()}).then(function(r){ setStatus(r.ok,r.ok?'Key saved.':'Failed'); });
  }
  function removeFolder(id){
    postJson('/folder/remove',{id:id}).then(function(r){ setStatus(r.ok,'Removed.'); refresh(); });
  }

  // ── Structured folder browser ──
  function fRow(name, onclick){
    return '<div class="folder-row" onclick="'+onclick+'"><span>📁</span><span>'+name+'</span></div>';
  }
  function renderRoot(){
    path = [];
    document.getElementById('crumbs').innerHTML = '<span class="crumb">Drives</span>';
    document.getElementById('addRow').classList.add('hidden');
    var html='';
    drives.forEach(function(d){ html += fRow(d.name, "pickDrive('"+d.id+"','"+esc(d.name)+"')"); });
    document.getElementById('browseList').innerHTML = html || '<div class="meta">No drives.</div>';
  }
  function pickDrive(id, name){ path = [{driveId:id, folderId:id, name:name}]; browse(); }
  function enter(folderId, name){ var d=path[0].driveId; path.push({driveId:d, folderId:folderId, name:name}); browse(); }
  function jumpTo(i){ path = path.slice(0, i+1); browse(); }
  function browse(){
    var cur = path[path.length-1];
    postJson('/browse', {driveId:cur.driveId, folderId:cur.folderId}).then(function(res){
      var ch = '<span class="crumb" onclick="renderRoot()">Drives</span>';
      path.forEach(function(p, i){ ch += '  ›  <span class="crumb" onclick="jumpTo('+i+')">'+p.name+'</span>'; });
      document.getElementById('crumbs').innerHTML = ch;
      var html='';
      (res.folders||[]).forEach(function(f){ html += fRow(f.name, "enter('"+f.id+"','"+esc(f.name)+"')"); });
      document.getElementById('browseList').innerHTML = html || '<div class="meta">No sub-folders here. You can add this folder below.</div>';
      document.getElementById('addRow').classList.remove('hidden');
    });
  }
  function addCurrent(type){
    var cur = path[path.length-1];
    postJson('/folder/add',{driveId:cur.driveId, folderId:cur.folderId, type:type}).then(function(r){
      setStatus(r.ok, r.ok?'Folder added.':'Failed'); refresh();
    });
  }

  // ── Backup ──
  function loadImport(e){
    var f=e.target.files[0]; if(!f) return;
    var reader=new FileReader(); reader.onload=function(){ importJson=reader.result; }; reader.readAsText(f);
  }
  function doImport(){
    if(!importJson){ setStatus(false,'Choose a backup file first.'); return; }
    postJson('/import',{json:importJson}).then(function(r){ setStatus(r.ok, r.ok?'Restored.':'Invalid backup file'); refresh(); });
  }

  refresh();
</script>
</body>
</html>
""".trimIndent()
}
