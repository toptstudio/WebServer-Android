package www.webserver.com;

public class WebUiJs {
    public static final String JS_FUNCTIONS =
"function formatFileSize(bytes){if(bytes===0)return'0 B';var k=1024,sizes=['B','KB','MB','GB'];var i=Math.floor(Math.log(bytes)/Math.log(k));return parseFloat((bytes/Math.pow(k,i)).toFixed(2))+' '+sizes[i];}\n"+
"var pendingFiles=[];\n"+
"var currentFileIndex=0;\n"+
"var currentUploadFile=null;\n"+
"var uploadToken=null;\n"+
"var neededChunks=null;\n"+
"var chunksMode=false;\n"+
"var CONCURRENCY=4;\n"+
"var totalChunksCount=0;\n"+
"var completedCount=0;\n"+
"var overwriteFlag=false;\n"+
"var processingItems={};\n"+
"var activeXHRs=[];\n"+
"var hostHtmlEnabled=false;\n"+
"function isItemBusy(name){if(processingItems[name]){alert('An operation is already in progress on \"'+name+'\". Please wait.');return true;}return false;}\n"+
"function downloadItem(name,isDir){if(isItemBusy(name))return;processingItems[name]=true;var url=currentFolder+'?download='+(isDir?'zip':'file')+'&path='+encodeURIComponent(name);window.location.href=url;setTimeout(function(){delete processingItems[name];},1000);}\n"+
"window.addEventListener('beforeunload',function(e){if(pendingFiles.length>0&&!document.getElementById('uploadBtn').disabled){e.preventDefault();e.returnValue='You have pending files. Leaving will discard them.';}activeXHRs.forEach(function(xhr){xhr.abort();});activeXHRs=[];});\n"+
"document.getElementById('chooseFileBtn').addEventListener('click',function(){document.getElementById('myFile').click();});\n"+
"document.getElementById('myFile').addEventListener('change',function(e){var files=e.target.files;if(!files.length){document.getElementById('fileInfoCard').style.display='none';pendingFiles=[];return;}pendingFiles=Array.from(files);currentFileIndex=0;updateInfoCardForFile(pendingFiles[0],1);resetCardStatus();document.getElementById('clearFileBtn').style.display='inline-block';this.value='';});\n"+
"function updateInfoCardForFile(file,number){document.getElementById('fileNameDisplay').textContent=(pendingFiles.length>1?'['+number+'/'+pendingFiles.length+'] ':'')+file.name;document.getElementById('fileSizeDisplay').textContent=formatFileSize(file.size);var ext=file.name.split('.').pop().toLowerCase();document.getElementById('fileExtDisplay').textContent=ext||'—';var iconEl=document.getElementById('fileIconDisplay');if(['jpg','jpeg','png','gif','bmp','webp'].includes(ext))iconEl.textContent='🖼️';else if(['mp4','mov','avi','mkv','webm'].includes(ext))iconEl.textContent='🎬';else if(['mp3','wav','ogg','flac'].includes(ext))iconEl.textContent='🎵';else if(['zip','rar','7z','tar','gz'].includes(ext))iconEl.textContent='🗜️';else if(['pdf'].includes(ext))iconEl.textContent='📕';else iconEl.textContent='📄';document.getElementById('fileInfoCard').style.display='flex';document.getElementById('clearFileBtn').style.display='inline-block';}\n"+
"function showFileResult(status,message){var meta=document.querySelector('.file-meta');if(!meta){meta=document.createElement('div');meta.className='file-meta';document.querySelector('.file-details').appendChild(meta);}var old=meta.querySelector('.file-status');if(old)old.remove();var span=document.createElement('span');span.className='file-status';span.style.fontWeight='bold';span.style.color=(status==='uploaded')?'#2e7d32':'#e53935';span.textContent=message;meta.appendChild(span);}\n"+
"function resetCardStatus(){var old=document.querySelector('.file-status');if(old)old.remove();}\n"+
"function resetUploadState(){uploadToken=null;neededChunks=null;overwriteFlag=false;totalChunksCount=0;completedCount=0;}\n"+
"function advanceQueue(){resetUploadState();currentFileIndex++;if(currentFileIndex<pendingFiles.length){var nextFile=pendingFiles[currentFileIndex];currentUploadFile=nextFile;updateInfoCardForFile(nextFile,currentFileIndex+1);resetCardStatus();var btn=document.getElementById('uploadBtn');btn.style.background='';btn.textContent='Analysing…';btn.disabled=false;startUpload(false);}else{var btn=document.getElementById('uploadBtn');btn.style.background='#9e9e9e';btn.textContent='All done';btn.disabled=true;loadDirectory(currentFolder);setTimeout(function(){btn.style.background='';btn.textContent='Upload';btn.disabled=false;document.getElementById('myFile').disabled=false;document.getElementById('chunksMode').disabled=false;document.getElementById('chooseFileBtn').disabled=false;document.getElementById('clearFileBtn').style.display='none';document.getElementById('fileInfoCard').style.display='none';pendingFiles=[];currentFileIndex=0;resetUploadState();},500);}}\n"+
"function skipCurrentFile(reason){var msg;if(reason==='identical')msg='Skipped (identical)';else if(reason==='duplicate')msg='Skipped (duplicate)';else if(reason==='cancelled')msg='Skipped (cancelled)';else msg='Error: '+reason;showFileResult('skipped',msg);advanceQueue();}\n"+
"document.getElementById('clearFileBtn').addEventListener('click',function(){pendingFiles=[];currentFileIndex=0;document.getElementById('myFile').value='';document.getElementById('fileInfoCard').style.display='none';document.getElementById('clearFileBtn').style.display='none';var btn=document.getElementById('uploadBtn');btn.disabled=false;btn.style.background='';btn.textContent='Upload';resetUploadState();});\n"+
"document.getElementById('chunksMode').addEventListener('change',function(){chunksMode=this.checked;});\n"+
"document.getElementById('uploadForm').addEventListener('submit',function(e){e.preventDefault();if(!pendingFiles.length)return;currentFileIndex=0;currentUploadFile=pendingFiles[0];resetUploadState();startUpload(false);});\n"+
"function startUpload(overwrite){\n"+
"    var file = currentUploadFile;\n"+
"    var btn = document.getElementById('uploadBtn');\n"+
"    btn.disabled = true;\n"+
"    btn.style.background = '';\n"+
"    btn.textContent = 'Checking…';\n"+
"    overwriteFlag = overwrite;\n"+
"    var totalSize = file.size;\n"+
"    var isSmall = totalSize <= 10 * 1024 * 1024;\n"+
"    var blobToSend;\n"+
"    if (isSmall) {\n"+
"        blobToSend = file;\n"+
"    } else {\n"+
"        var sliceSize = 1024 * 1024;\n"+
"        var slices = [];\n"+
"        slices.push(file.slice(0, sliceSize));\n"+
"        var midStart = Math.floor(totalSize / 2 - sliceSize / 2);\n"+
"        slices.push(file.slice(midStart, midStart + sliceSize));\n"+
"        slices.push(file.slice(totalSize - sliceSize, totalSize));\n"+
"        blobToSend = new Blob(slices);\n"+
"    }\n"+
"    var reader = new FileReader();\n"+
"    reader.onload = function(e) {\n"+
"        var rawBytes = e.target.result;\n"+
"        var xhr = new XMLHttpRequest();\n"+
"        xhr.withCredentials = true;\n"+
"        activeXHRs.push(xhr);\n"+
"        var mode = chunksMode ? 'chunks' : 'normal';\n"+
"        var qs = '?name=' + encodeURIComponent(file.name) + '&size=' + totalSize + '&mode=' + mode;\n"+
"        if (overwrite) qs += '&overwrite=true';\n"+
"        xhr.open('POST', currentFolder + qs, true);\n"+
"        xhr.setRequestHeader('Content-Type', 'application/octet-stream');\n"+
"        xhr.timeout = 60000;\n"+
"        xhr.ontimeout = function() {\n"+
"            activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"            btn.style.background = '#e53935'; btn.textContent = 'Server timeout'; btn.disabled = false;\n"+
"            showFileResult('error', 'Server did not respond within 60 seconds.');\n"+
"            skipCurrentFile('offline');\n"+
"        };\n"+
"        xhr.onload = function() {\n"+
"            activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"            var btn = document.getElementById('uploadBtn');\n"+
"            if (xhr.status === 200) {\n"+
"                try {\n"+
"                    var resp = JSON.parse(xhr.responseText);\n"+
"                    if (resp.status === 'identical') {\n"+
"                        btn.style.background = '#e53935'; btn.textContent = 'Exist!'; btn.disabled = true;\n"+
"                        showFileResult('skipped', 'Identical file already exists – skipped.');\n"+
"                        skipCurrentFile('identical');\n"+
"                    } else if (resp.status === 'ok' || resp.status === 'complete') {\n"+
"                        uploadToken = resp.token || null;\n"+
"                        if (resp.needed) {\n"+
"                            neededChunks = resp.needed.split(',').map(Number);\n"+
"                            neededChunks.sort(function(a,b){return a-b;});\n"+
"                        } else {\n"+
"                            neededChunks = null;\n"+
"                        }\n"+
"                        completedCount = resp.completed || 0;\n"+
"                        totalChunksCount = neededChunks ? (neededChunks.length + completedCount) : completedCount;\n"+
"                        if (chunksMode && neededChunks && neededChunks.length > 0) {\n"+
"                            btn.textContent = completedCount > 0 ? 'Resuming from chunk ' + completedCount + '/' + totalChunksCount + '…' : 'Starting parallel upload…';\n"+
"                            uploadChunksInParallel(file, neededChunks, overwrite, null);\n"+
"                        } else {\n"+
"                            btn.textContent = 'Uploading 0%';\n"+
"                            uploadFileDirect(file, overwrite);\n"+
"                        }\n"+
"                    } else if (resp.status === 'duplicate') {\n"+
"                        showUploadDuplicateDialog(resp.name, function(action) {\n"+
"                            if (action === 'replace') {\n"+
"                                startUpload(true);\n"+
"                            } else if (action === 'skip') {\n"+
"                                skipAndRenameFile();\n"+
"                            } else {\n"+
"                                btn.disabled = false; btn.style.background = ''; btn.textContent = 'Upload';\n"+
"                                showFileResult('skipped', 'Skipped (cancelled)');\n"+
"                                skipCurrentFile('duplicate');\n"+
"                            }\n"+
"                        });\n"+
"                    } else {\n"+
"                        btn.style.background = '#e53935'; btn.textContent = resp.message || 'Error'; btn.disabled = false;\n"+
"                        showFileResult('error', 'Error: ' + (resp.message || 'Unknown'));\n"+
"                        skipCurrentFile('error');\n"+
"                    }\n"+
"                } catch(e) {\n"+
"                    btn.style.background = '#e53935'; btn.textContent = 'Invalid response'; btn.disabled = false;\n"+
"                    showFileResult('error', 'Invalid response');\n"+
"                    skipCurrentFile('error');\n"+
"                }\n"+
"            } else if (xhr.status === 401) {\n"+
"                btn.style.background = '#e53935'; btn.textContent = 'Auth required'; btn.disabled = false;\n"+
"                showFileResult('error', 'Authentication required – please log in first (try any rename or delete action).');\n"+
"            } else {\n"+
"                btn.style.background = '#e53935'; btn.textContent = 'HTTP ' + xhr.status; btn.disabled = false;\n"+
"                showFileResult('error', 'HTTP ' + xhr.status);\n"+
"                skipCurrentFile('error');\n"+
"            }\n"+
"        };\n"+
"        xhr.onerror = function() {\n"+
"            activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"            btn.style.background = '#e53935'; btn.textContent = 'Network error'; btn.disabled = false;\n"+
"            showFileResult('error', 'Network error');\n"+
"            skipCurrentFile('error');\n"+
"        };\n"+
"        xhr.send(rawBytes);\n"+
"    };\n"+
"    reader.readAsArrayBuffer(blobToSend);\n"+
"}\n"+
"function skipAndRenameFile() {\n"+
"    var file = currentUploadFile;\n"+
"    var dot = file.name.lastIndexOf('.');\n"+
"    var base = dot > 0 ? file.name.substring(0, dot) : file.name;\n"+
"    var ext = dot > 0 ? file.name.substring(dot) : '';\n"+
"    var newName = base + ' 1' + ext;\n"+
"    var newFile = new File([file], newName, {type: file.type});\n"+
"    currentUploadFile = newFile;\n"+
"    resetUploadState();\n"+
"    startUpload(false);\n"+
"}\n"+
"function uploadFileDirect(file, overwrite) {\n"+
"    var d = new FormData();\n"+
"    d.append('filename', file, file.name);\n"+
"    var b = document.getElementById('uploadBtn');\n"+
"    b.disabled = true; b.style.background = ''; b.textContent = 'Uploading 0%';\n"+
"    var x = new XMLHttpRequest();\n"+
"    x.withCredentials = true;\n"+
"    activeXHRs.push(x);\n"+
"    var url = currentFolder;\n"+
"    var params = [];\n"+
"    if (uploadToken) params.push('token=' + encodeURIComponent(uploadToken));\n"+
"    if (overwrite) params.push('overwrite=true');\n"+
"    if (params.length > 0) url += '?' + params.join('&');\n"+
"    x.open('POST', url, true);\n"+
"    x.upload.addEventListener('progress', function(e) {\n"+
"        if (e.lengthComputable) {\n"+
"            var p = Math.round((e.loaded / e.total) * 100);\n"+
"            b.textContent = 'Uploading ' + p + '%';\n"+
"        }\n"+
"    });\n"+
"    x.onload = function() {\n"+
"        activeXHRs = activeXHRs.filter(function(h) { return h !== x; });\n"+
"        if (x.status === 200) {\n"+
"            try {\n"+
"                var resp = JSON.parse(x.responseText);\n"+
"                if (resp.status === 'ok' || resp.status === 'complete') {\n"+
"                    showFileResult('uploaded', 'Uploaded');\n"+
"                    advanceQueue();\n"+
"                } else {\n"+
"                    b.style.background = '#e53935'; b.textContent = resp.message || 'Failed!'; b.disabled = false;\n"+
"                    showFileResult('error', 'Failed: ' + (resp.message || ''));\n"+
"                    skipCurrentFile('error');\n"+
"                }\n"+
"            } catch(e) {\n"+
"                b.style.background = '#e53935'; b.textContent = 'Invalid response'; b.disabled = false;\n"+
"                showFileResult('error', 'Invalid response');\n"+
"                skipCurrentFile('error');\n"+
"            }\n"+
"        } else {\n"+
"            b.style.background = '#e53935'; b.textContent = 'HTTP ' + x.status; b.disabled = false;\n"+
"            showFileResult('error', 'HTTP ' + x.status);\n"+
"            skipCurrentFile('error');\n"+
"        }\n"+
"    };\n"+
"    x.onerror = function() {\n"+
"        activeXHRs = activeXHRs.filter(function(h) { return h !== x; });\n"+
"        b.style.background = '#e53935'; b.textContent = 'Network error'; b.disabled = false;\n"+
"        showFileResult('error', 'Network error');\n"+
"        skipCurrentFile('error');\n"+
"    };\n"+
"    x.send(d);\n"+
"}\n"+
"async function uploadChunksInParallel(file, chunks, overwrite, newName) {\n"+
"    var chunkSize = 10 * 1024 * 1024;\n"+
"    var totalNeeded = chunks.length;\n"+
"    var btn = document.getElementById('uploadBtn');\n"+
"    async function uploadSingle(idx) {\n"+
"        var start = idx * chunkSize;\n"+
"        var end = Math.min(start + chunkSize, file.size);\n"+
"        var blob = file.slice(start, end);\n"+
"        var form = new FormData();\n"+
"        form.append('chunk_' + ('00000' + idx).slice(-5) + '.dat', blob, 'chunk_' + ('00000' + idx).slice(-5) + '.dat');\n"+
"        var xhr = new XMLHttpRequest();\n"+
"        xhr.withCredentials = true;\n"+
"        activeXHRs.push(xhr);\n"+
"        var url = currentFolder + '?token=' + encodeURIComponent(uploadToken);\n"+
"        if (newName) url += '&newname=' + encodeURIComponent(newName);\n"+
"        return new Promise(function(resolve, reject) {\n"+
"            xhr.open('POST', url, true);\n"+
"            xhr.onload = function() {\n"+
"                activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"                if (xhr.status === 200) {\n"+
"                    try {\n"+
"                        var resp = JSON.parse(xhr.responseText);\n"+
"                        if (resp.status === 'ok' || resp.status === 'complete') {\n"+
"                            completedCount++;\n"+
"                            btn.textContent = 'Uploading chunk ' + completedCount + '/' + totalChunksCount;\n"+
"                            resolve();\n"+
"                        } else {\n"+
"                            reject(new Error(resp.message || 'Chunk failed'));\n"+
"                        }\n"+
"                    } catch(e) { reject(e); }\n"+
"                } else {\n"+
"                    reject(new Error('HTTP ' + xhr.status));\n"+
"                }\n"+
"            };\n"+
"            xhr.onerror = function() { \n"+
"                activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"                reject(new Error('Network error')); \n"+
"            };\n"+
"            xhr.send(form);\n"+
"        });\n"+
"    }\n"+
"    var queue = chunks.slice();\n"+
"    async function worker() {\n"+
"        while (queue.length > 0) {\n"+
"            var idx = queue.shift();\n"+
"            await uploadSingle(idx);\n"+
"        }\n"+
"    }\n"+
"    var workers = [];\n"+
"    for (var i = 0; i < CONCURRENCY; i++) workers.push(worker());\n"+
"    try {\n"+
"        await Promise.all(workers);\n"+
"        await finalizeUpload();\n"+
"        showFileResult('uploaded', 'Uploaded');\n"+
"        advanceQueue();\n"+
"    } catch(err) {\n"+
"        btn.style.background = '#e53935'; btn.textContent = 'Upload failed: ' + err.message; btn.disabled = false;\n"+
"        showFileResult('error', 'Failed: ' + err.message);\n"+
"        skipCurrentFile('error');\n"+
"    }\n"+
"}\n"+
"async function finalizeUpload() {\n"+
"    return new Promise(function(resolve, reject) {\n"+
"        var xhr = new XMLHttpRequest();\n"+
"        xhr.withCredentials = true;\n"+
"        activeXHRs.push(xhr);\n"+
"        var url = currentFolder + '?token=' + encodeURIComponent(uploadToken) + '&action=finalize';\n"+
"        if (overwriteFlag) url += '&overwrite=true';\n"+
"        xhr.open('POST', url, true);\n"+
"        xhr.setRequestHeader('Content-Type', 'text/plain');\n"+
"        xhr.onload = function() {\n"+
"            activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"            if (xhr.status === 200) {\n"+
"                try {\n"+
"                    var resp = JSON.parse(xhr.responseText);\n"+
"                    if (resp.status === 'ok' || resp.status === 'complete') {\n"+
"                        resolve();\n"+
"                    } else if (resp.status === 'incomplete' && resp.needed) {\n"+
"                        var missing = resp.needed.split(',').map(Number).sort(function(a,b){return a-b;});\n"+
"                        uploadChunksInParallel(currentUploadFile, missing, overwriteFlag, null).then(resolve).catch(reject);\n"+
"                    } else {\n"+
"                        reject(new Error(resp.message || 'Finalize failed'));\n"+
"                    }\n"+
"                } catch(e) { reject(e); }\n"+
"            } else {\n"+
"                reject(new Error('HTTP ' + xhr.status));\n"+
"            }\n"+
"        };\n"+
"        xhr.onerror = function() { \n"+
"            activeXHRs = activeXHRs.filter(function(h) { return h !== xhr; });\n"+
"            reject(new Error('Network error')); \n"+
"        };\n"+
"        xhr.send('finalize');\n"+
"    });\n"+
"}\n"+
"function showUploadDuplicateDialog(name, callback) {\n"+
"    var safeName = escapeHtml(name);\n"+
"    var isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme:dark)').matches;\n"+
"    var overlay = document.createElement('div');\n"+
"    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:9999;';\n"+
"    var box = document.createElement('div');\n"+
"    box.style.cssText = 'background:' + (isDark ? '#2a2a2a' : 'white') + ';padding:20px;border-radius:10px;box-shadow:0 4px 20px rgba(0,0,0,0.3);max-width:400px;width:90%;color:' + (isDark ? '#ddd' : '#333') + ';';\n"+
"    var dialogTimerId = null;\n"+
"    box.innerHTML = '<h3 style=\"margin-top:0;color:' + (isDark ? '#fff' : '#000') + '\">File already exists</h3>' +\n"+
"        '<p style=\"margin-bottom:10px\">' + safeName + ' already exists. What would you like to do?</p>' +\n"+
"        '<div style=\"margin-top:10px\">' +\n"+
"        '<button id=\"dup-replace\" style=\"background:#e53935;color:white;border:none;padding:10px 20px;margin-right:10px;border-radius:5px;cursor:pointer\">Replace</button>' +\n"+
"        '<button id=\"dup-skip\" style=\"background:#2196F3;color:white;border:none;padding:10px 20px;margin-right:10px;border-radius:5px;cursor:pointer\">Skip</button>' +\n"+
"        '<button id=\"dup-cancel\" style=\"background:' + (isDark ? '#555' : '#f1f3f4') + ';border:1px solid #ccc;padding:10px 20px;border-radius:5px;cursor:pointer;color:' + (isDark ? '#ddd' : '#333') + '\">Cancel</button>' +\n"+
"        '</div>' +\n"+
"        '<p style=\"margin-top:10px;font-size:12px;color:' + (isDark ? '#aaa' : '#888') + ';text-align:center;\" id=\"dup-timer\">Auto‑skip in 60s</p>';\n"+
"    overlay.appendChild(box);\n"+
"    document.body.appendChild(overlay);\n"+
"    var timerSec = 60;\n"+
"    var timerEl = document.getElementById('dup-timer');\n"+
"    function updateTimer() {\n"+
"        if (timerSec <= 0) {\n"+
"            clearTimeout(dialogTimerId);\n"+
"            overlay.remove();\n"+
"            callback('skip');\n"+
"            return;\n"+
"        }\n"+
"        timerEl.textContent = 'Auto‑skip in ' + timerSec + 's';\n"+
"        timerSec--;\n"+
"        dialogTimerId = setTimeout(updateTimer, 1000);\n"+
"    }\n"+
"    dialogTimerId = setTimeout(updateTimer, 1000);\n"+
"    document.getElementById('dup-replace').addEventListener('click', function() {\n"+
"        clearTimeout(dialogTimerId);\n"+
"        overlay.remove();\n"+
"        callback('replace');\n"+
"    });\n"+
"    document.getElementById('dup-skip').addEventListener('click', function() {\n"+
"        clearTimeout(dialogTimerId);\n"+
"        overlay.remove();\n"+
"        callback('skip');\n"+
"    });\n"+
"    document.getElementById('dup-cancel').addEventListener('click', function() {\n"+
"        clearTimeout(dialogTimerId);\n"+
"        overlay.remove();\n"+
"        callback('cancel');\n"+
"    });\n"+
"}\n"+
"function showRenameDuplicateDialog(name) {\n"+
"    var safeName = escapeHtml(name);\n"+
"    var isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme:dark)').matches;\n"+
"    var overlay = document.createElement('div');\n"+
"    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:9999;';\n"+
"    var box = document.createElement('div');\n"+
"    box.style.cssText = 'background:' + (isDark ? '#2a2a2a' : 'white') + ';padding:20px;border-radius:10px;box-shadow:0 4px 20px rgba(0,0,0,0.3);max-width:400px;width:90%;color:' + (isDark ? '#ddd' : '#333') + ';';\n"+
"    box.innerHTML = '<h3 style=\"margin-top:0;color:' + (isDark ? '#fff' : '#000') + '\">Item already exists</h3>' +\n"+
"        '<p style=\"margin-bottom:10px\">' + safeName + ' already exists. What would you like to do?</p>' +\n"+
"        '<div style=\"margin-top:10px\">' +\n"+
"        '<button id=\"dup-replace\" style=\"background:#e53935;color:white;border:none;padding:10px 20px;margin-right:10px;border-radius:5px;cursor:pointer\">Replace</button>' +\n"+
"        '<button id=\"dup-skip\" style=\"background:#2196F3;color:white;border:none;padding:10px 20px;margin-right:10px;border-radius:5px;cursor:pointer\">Skip</button>' +\n"+
"        '<button id=\"dup-cancel\" style=\"background:' + (isDark ? '#555' : '#f1f3f4') + ';border:1px solid #ccc;padding:10px 20px;border-radius:5px;cursor:pointer;color:' + (isDark ? '#ddd' : '#333') + '\">Cancel</button>' +\n"+
"        '</div>';\n"+
"    overlay.appendChild(box);\n"+
"    document.body.appendChild(overlay);\n"+
"    document.getElementById('dup-replace').addEventListener('click', function() {\n"+
"        overlay.remove();\n"+
"        sendAction('action=rename&name=' + encodeURIComponent(currentRenameName) + '&newname=' + encodeURIComponent(currentNewName) + '&overwrite=true').then(function(j) {\n"+
"            if (j.status === 'ok') loadDirectory(currentFolder);\n"+
"            else alert(j.message || 'Replace failed');\n"+
"        });\n"+
"    });\n"+
"    document.getElementById('dup-skip').addEventListener('click', function() {\n"+
"        overlay.remove();\n"+
"        var newBase = currentNewName;\n"+
"        var dot = newBase.lastIndexOf('.');\n"+
"        var base = dot > 0 ? newBase.substring(0, dot) : newBase;\n"+
"        var ext = dot > 0 ? newBase.substring(dot) : '';\n"+
"        var counter = 1;\n"+
"        var maxTries = 100;\n"+
"        function attemptRename() {\n"+
"            if (counter > maxTries) {\n"+
"                alert('Too many duplicates – rename failed.');\n"+
"                return;\n"+
"            }\n"+
"            var tryName = base + ' ' + counter + ext;\n"+
"            sendAction('action=rename&name=' + encodeURIComponent(currentRenameName) + '&newname=' + encodeURIComponent(tryName)).then(function(j) {\n"+
"                if (j.status === 'ok') {\n"+
"                    loadDirectory(currentFolder);\n"+
"                } else if (j.status === 'duplicate') {\n"+
"                    counter++;\n"+
"                    attemptRename();\n"+
"                } else alert(j.message || 'Skip failed');\n"+
"            });\n"+
"        }\n"+
"        attemptRename();\n"+
"    });\n"+
"    document.getElementById('dup-cancel').addEventListener('click', function() {\n"+
"        overlay.remove();\n"+
"    });\n"+
"}\n"+
"function sendAction(p) { return fetch(currentFolder, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: p }).then(function(r) { return r.json(); }); }\n"+
"function deleteItem(n) { if (isItemBusy(n)) return; if (!confirm('Delete ' + n + '?')) return; processingItems[n] = true; sendAction('action=delete&name=' + encodeURIComponent(n)).then(function(j) { delete processingItems[n]; if (j.status === 'ok') loadDirectory(currentFolder); else alert(j.message || 'Delete failed'); }).catch(function(e) { delete processingItems[n]; alert('Delete request failed: ' + e); }); }\n"+
"var currentRenameName=''; var currentNewName='';\n"+
"function renameItem(n) { if (isItemBusy(n)) return; var nn = prompt('Enter new name for ' + n, n); if (!nn || nn === n) return; processingItems[n] = true; currentRenameName = n; currentNewName = nn; sendAction('action=rename&name=' + encodeURIComponent(n) + '&newname=' + encodeURIComponent(nn)).then(function(j) { delete processingItems[n]; if (j.status === 'ok') loadDirectory(currentFolder); else if (j.status === 'duplicate') { showRenameDuplicateDialog(nn); } else { alert(j.message || 'Rename failed'); } }).catch(function(e) { delete processingItems[n]; alert('Rename request failed: ' + e); }); }\n"+
"function createFolder() { var n = prompt('Enter new folder name', 'New Folder'); if (!n) return; sendAction('action=mkdir&name=' + encodeURIComponent(n)).then(function(j) { if (j.status === 'ok') loadDirectory(currentFolder); else alert(j.message || 'Create folder failed'); }); }\n"+
"function createFile() { var n = prompt('Enter new file name', 'newfile.txt'); if (!n) return; sendAction('action=mkfile&name=' + encodeURIComponent(n)).then(function(j) { if (j.status === 'ok') loadDirectory(currentFolder); else alert(j.message || 'Create file failed'); }); }\n"+
"function loadDirectory(path) {\n"+
"    if (!path) return;\n"+
"    var url = path;\n"+
"    if (!url.startsWith('/')) url = '/' + url;\n"+
"    var xhr = new XMLHttpRequest();\n"+
"    xhr.open('GET', url + '?json=1&_=' + Date.now(), true);\n"+
"    xhr.setRequestHeader('Accept', 'application/json');\n"+
"    xhr.onload = function() {\n"+
"        if (xhr.status === 200) {\n"+
"            try {\n"+
"                var data = JSON.parse(xhr.responseText);\n"+
"                if (data.status === 'ok') {\n"+
"                    currentFolder = url;\n"+
"                    if (!currentFolder.endsWith('/')) currentFolder += '/';\n"+
"                    history.pushState({ folder: currentFolder }, '', currentFolder);\n"+
"                    renderDirectory(data.items, data.authenticated, data.parentExists, data.truncated, data.total, data.offset, data.limit);\n"+
"                } else {\n"+
"                    alert('Failed to load directory');\n"+
"                }\n"+
"            } catch(e) {\n"+
"                alert('Invalid directory response');\n"+
"            }\n"+
"        } else {\n"+
"            alert('HTTP ' + xhr.status);\n"+
"        }\n"+
"    };\n"+
"    xhr.onerror = function() { alert('Network error'); };\n"+
"    xhr.send();\n"+
"}\n"+
"function renderDirectory(items, authenticated, parentExists, truncated, total, currentOffset, limit) {\n"+
"    var container = document.querySelector('.container');\n"+
"    var uploadSection = container.querySelector('.upload-section');\n"+
"    var folderBtns = container.querySelectorAll('.folder-btn-container');\n"+
"    var footer = container.querySelector('.footer');\n"+
"    var oldTable = container.querySelector('table');\n"+
"    if (oldTable) oldTable.remove();\n"+
"    var table = document.createElement('table');\n"+
"    var thead = document.createElement('thead');\n"+
"    var actionCol = '';\n"+
"    if (authenticated) { actionCol = '<th style=\"width:20%;text-align:center\">Actions</th>'; }\n"+
"    thead.innerHTML = '<tr><th style=\"width:50%\">Name</th><th style=\"width:20%;text-align:center\">Size</th>' + actionCol + '<th style=\"width:10%;text-align:right\">Download</th></tr>';\n"+
"    table.appendChild(thead);\n"+
"    var tbody = document.createElement('tbody');\n"+
"    if (parentExists) {\n"+
"        var pathNoTrail = currentFolder.endsWith('/') ? currentFolder.slice(0, -1) : currentFolder;\n"+
"        var parentPath = '/';\n"+
"        var lastSlash = pathNoTrail.lastIndexOf('/');\n"+
"        if (lastSlash > 0) parentPath = pathNoTrail.substring(0, lastSlash);\n"+
"        var row = document.createElement('tr');\n"+
"        var colspan = authenticated ? 4 : 3;\n"+
"        var td = document.createElement('td');\n"+
"        td.className = 'name';\n"+
"        td.setAttribute('colspan', colspan);\n"+
"        var a = document.createElement('a');\n"+
"        a.href = parentPath;\n"+
"        a.className = 'dir-link';\n"+
"        a.innerHTML = '<span class=\"parent-icon\"></span>Parent Directory';\n"+
"        td.appendChild(a);\n"+
"        row.appendChild(td);\n"+
"        tbody.appendChild(row);\n"+
"    }\n"+
"    items.forEach(function(item) {\n"+
"        var row = document.createElement('tr');\n"+
"        var nameCell = document.createElement('td');\n"+
"        nameCell.className = 'name';\n"+
"        var link = document.createElement('a');\n"+
"        var itemAbs = currentFolder + encodeURIComponent(item.name);\n"+
"        if (item.isDirectory) {\n"+
"            link.href = itemAbs;\n"+
"            link.className = 'dir-link';\n"+
"            link.innerHTML = '<span class=\"folder-icon\"></span>' + escapeHtml(item.name);\n"+
"        } else {\n"+
"            link.href = itemAbs;\n"+
"            link.className = 'file-link';\n"+
"            link.innerHTML = '<span class=\"file-icon\"></span>' + escapeHtml(item.name);\n"+
"        }\n"+
"        nameCell.appendChild(link);\n"+
"        row.appendChild(nameCell);\n"+
"        var sizeCell = document.createElement('td');\n"+
"        sizeCell.className = 'size';\n"+
"        sizeCell.style.textAlign = 'center';\n"+
"        if (item.isDirectory) { sizeCell.textContent = '-'; }\n"+
"        else { sizeCell.textContent = formatFileSize(item.size); }\n"+
"        row.appendChild(sizeCell);\n"+
"        if (authenticated) {\n"+
"            var actionCell = document.createElement('td');\n"+
"            actionCell.style.textAlign = 'center';\n"+
"            var delBtn = document.createElement('button');\n"+
"            delBtn.textContent = '🗑️';\n"+
"            delBtn.title = 'Delete';\n"+
"            delBtn.onclick = function() { deleteItem(item.name); };\n"+
"            var renBtn = document.createElement('button');\n"+
"            renBtn.textContent = '📝';\n"+
"            renBtn.title = 'Rename';\n"+
"            renBtn.onclick = function() { renameItem(item.name); };\n"+
"            actionCell.appendChild(delBtn);\n"+
"            actionCell.appendChild(document.createTextNode(' '));\n"+
"            actionCell.appendChild(renBtn);\n"+
"            row.appendChild(actionCell);\n"+
"        }\n"+
"        var dlCell = document.createElement('td');\n"+
"        dlCell.className = 'download';\n"+
"        var dlBtn = document.createElement('button');\n"+
"        dlBtn.textContent = '📥';\n"+
"        dlBtn.title = 'Download';\n"+
"        dlBtn.onclick = function() { downloadItem(item.name, item.isDirectory); };\n"+
"        dlCell.appendChild(dlBtn);\n"+
"        row.appendChild(dlCell);\n"+
"        tbody.appendChild(row);\n"+
"    });\n"+
"    if (truncated) {\n"+
"        var moreRow = document.createElement('tr');\n"+
"        moreRow.className = 'more-row';\n"+
"        var moreTd = document.createElement('td');\n"+
"        var colspan = authenticated ? 4 : 3;\n"+
"        moreTd.setAttribute('colspan', colspan);\n"+
"        var moreLink = document.createElement('a');\n"+
"        moreLink.href = 'javascript:void(0)';\n"+
"        moreLink.textContent = 'More...';\n"+
"        moreLink.onclick = function() { loadMoreItems(currentOffset + items.length); };\n"+
"        moreTd.appendChild(moreLink);\n"+
"        moreRow.appendChild(moreTd);\n"+
"        tbody.appendChild(moreRow);\n"+
"        table.currentOffset = currentOffset + items.length;\n"+
"        table.totalItems = total;\n"+
"    }\n"+
"    table.appendChild(tbody);\n"+
"    container.insertBefore(table, uploadSection);\n"+
"    if (folderBtns.length > 0) { folderBtns.forEach(function(btnContainer) { container.appendChild(btnContainer); }); }\n"+
"    if (footer) container.appendChild(footer);\n"+
"    if (hostHtmlEnabled) {\n"+
"        var uploadEl = container.querySelector('.upload-section'); if (uploadEl) uploadEl.style.display = 'none';\n"+
"        var folderBtnEls = container.querySelectorAll('.folder-btn-container'); folderBtnEls.forEach(function(el) { el.style.display = 'none'; });\n"+
"        var uploadBtnEl = document.getElementById('uploadBtn'); if (uploadBtnEl) uploadBtnEl.style.display = 'none';\n"+
"    }\n"+
"}\n"+
"function loadMoreItems(offset) {\n"+
"    var xhr = new XMLHttpRequest();\n"+
"    var url = currentFolder + '?json=1&offset=' + offset + '&limit=500';\n"+
"    xhr.open('GET', url, true);\n"+
"    xhr.setRequestHeader('Accept', 'application/json');\n"+
"    xhr.onload = function() {\n"+
"        if (xhr.status === 200) {\n"+
"            try {\n"+
"                var data = JSON.parse(xhr.responseText);\n"+
"                if (data.status === 'ok') {\n"+
"                    var tbody = document.querySelector('table tbody');\n"+
"                    var moreRow = tbody.querySelector('.more-row');\n"+
"                    if (moreRow) moreRow.remove();\n"+
"                    data.items.forEach(function(item) {\n"+
"                        var row = document.createElement('tr');\n"+
"                        var nameCell = document.createElement('td'); nameCell.className = 'name';\n"+
"                        var link = document.createElement('a');\n"+
"                        var itemAbs = currentFolder + encodeURIComponent(item.name);\n"+
"                        if (item.isDirectory) {\n"+
"                            link.href = itemAbs;\n"+
"                            link.className = 'dir-link';\n"+
"                            link.innerHTML = '<span class=\"folder-icon\"></span>' + escapeHtml(item.name);\n"+
"                        } else {\n"+
"                            link.href = itemAbs;\n"+
"                            link.className = 'file-link';\n"+
"                            link.innerHTML = '<span class=\"file-icon\"></span>' + escapeHtml(item.name);\n"+
"                        }\n"+
"                        nameCell.appendChild(link);\n"+
"                        row.appendChild(nameCell);\n"+
"                        var sizeCell = document.createElement('td'); sizeCell.className = 'size'; sizeCell.style.textAlign = 'center';\n"+
"                        sizeCell.textContent = item.isDirectory ? '-' : formatFileSize(item.size);\n"+
"                        row.appendChild(sizeCell);\n"+
"                        if (data.authenticated) {\n"+
"                            var actionCell = document.createElement('td'); actionCell.style.textAlign = 'center';\n"+
"                            var delBtn = document.createElement('button'); delBtn.textContent = '🗑️'; delBtn.title = 'Delete';\n"+
"                            delBtn.onclick = function() { deleteItem(item.name); };\n"+
"                            var renBtn = document.createElement('button'); renBtn.textContent = '📝'; renBtn.title = 'Rename';\n"+
"                            renBtn.onclick = function() { renameItem(item.name); };\n"+
"                            actionCell.appendChild(delBtn); actionCell.appendChild(document.createTextNode(' ')); actionCell.appendChild(renBtn);\n"+
"                            row.appendChild(actionCell);\n"+
"                        }\n"+
"                        var dlCell = document.createElement('td'); dlCell.className = 'download';\n"+
"                        var dlBtn = document.createElement('button'); dlBtn.textContent = '📥'; dlBtn.title = 'Download';\n"+
"                        dlBtn.onclick = function() { downloadItem(item.name, item.isDirectory); };\n"+
"                        dlCell.appendChild(dlBtn);\n"+
"                        row.appendChild(dlCell);\n"+
"                        tbody.appendChild(row);\n"+
"                    });\n"+
"                    if (data.truncated) {\n"+
"                        var moreRow2 = document.createElement('tr'); moreRow2.className = 'more-row';\n"+
"                        var moreTd2 = document.createElement('td');\n"+
"                        var colspan = data.authenticated ? 4 : 3;\n"+
"                        moreTd2.setAttribute('colspan', colspan);\n"+
"                        var moreLink2 = document.createElement('a');\n"+
"                        moreLink2.href = 'javascript:void(0)';\n"+
"                        moreLink2.textContent = 'More...';\n"+
"                        moreLink2.onclick = function() { loadMoreItems(offset + data.items.length); };\n"+
"                        moreTd2.appendChild(moreLink2);\n"+
"                        moreRow2.appendChild(moreTd2);\n"+
"                        tbody.appendChild(moreRow2);\n"+
"                    }\n"+
"                }\n"+
"            } catch(e) { alert('Error loading more items'); }\n"+
"        }\n"+
"    };\n"+
"    xhr.send();\n"+
"}\n"+
"document.addEventListener('click', function(e) {\n"+
"    var link = e.target.closest('a.dir-link');\n"+
"    if (!link) return;\n"+
"    e.preventDefault();\n"+
"    var href = link.getAttribute('href');\n"+
"    if (href && href.indexOf('?download=') !== -1) {\n"+
"        window.location.href = href;\n"+
"        return;\n"+
"    }\n"+
"    var path = href.split('?')[0];\n"+
"    loadDirectory(path);\n"+
"});\n"+
"window.addEventListener('popstate', function(e) {\n"+
"    if (e.state && e.state.folder) { loadDirectory(e.state.folder); }\n"+
"});\n"+
"window.addEventListener('DOMContentLoaded', function() {\n"+
"    if (hostHtmlEnabled) {\n"+
"        var uploadEl = document.querySelector('.upload-section'); if (uploadEl) uploadEl.style.display = 'none';\n"+
"        var folderBtns = document.querySelectorAll('.folder-btn-container'); folderBtns.forEach(function(el) { el.style.display = 'none'; });\n"+
"        var uploadBtn = document.getElementById('uploadBtn'); if (uploadBtn) uploadBtn.style.display = 'none';\n"+
"    }\n"+
"});\n";
}