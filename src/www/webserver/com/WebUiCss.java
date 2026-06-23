package www.webserver.com;

public class WebUiCss {
    public static final String CSS =
"*{box-sizing:border-box;margin:0;padding:0}"+
"body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;background:#f0f2f5;color:#333;display:flex;justify-content:center;align-items:flex-start;min-height:100vh;padding:clamp(10px,3vw,30px)}"+
".container{width:100%;max-width:1200px;background:white;border-radius:clamp(8px,2vw,16px);box-shadow:0 2px 12px rgba(0,0,0,0.08);padding:clamp(10px,2vw,25px);user-select:none}"+
"table{width:100%;border-collapse:collapse;margin:15px 0}"+
"th,td{padding:clamp(8px,1.5vw,12px) clamp(6px,1vw,10px);text-align:left;border-bottom:1px solid #e9ecef;vertical-align:middle}"+
"th{background:#f8f9fa;font-weight:600;color:#555;font-size:clamp(0.8rem,1.5vw,0.95rem);letter-spacing:0.3px}"+
"td{font-size:clamp(0.75rem,1.4vw,0.9rem)}"+
"tr:hover{background:#f8f9fa}"+
".name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;user-select:text}"+
".name a{text-decoration:none;color:#1a73e8;display:flex;align-items:center;gap:5px}"+
".name a:hover{text-decoration:underline}"+
".folder-icon::before{content:'📁';margin-right:4px;vertical-align:middle}"+
".file-icon::before{content:'📄';margin-right:4px;vertical-align:middle}"+
".parent-icon::before{content:'📂';margin-right:4px;vertical-align:middle}"+
".size{color:#666;white-space:nowrap;text-align:right;font-family:monospace;user-select:text}"+
".size-num{display:inline-block;width:7ch;text-align:right}"+
".size-unit{display:inline-block;width:4ch;text-align:left}"+
"td button{background:#f1f3f4;border:1px solid #dadce0;border-radius:4px;padding:clamp(2px,0.5vw,4px) clamp(4px,1vw,8px);font-size:clamp(11px,1.2vw,13px);cursor:pointer;transition:background 0.2s;white-space:nowrap;line-height:1.2;vertical-align:middle}"+
"td button:hover{background:#e8eaed}"+
".download{text-align:center;vertical-align:middle}"+
".upload-section{margin-top:20px;padding:12px 0;border-top:1px dashed #ddd;border-bottom:1px dashed #ddd}"+
".upload-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}"+
".chunk-toggle{display:flex;align-items:center;gap:6px;font-size:13px;color:#555;white-space:nowrap}"+
".switch{position:relative;display:inline-block;width:40px;height:22px}"+
".switch input{opacity:0;width:0;height:0}"+
".slider{position:absolute;cursor:pointer;top:0;left:0;right:0;bottom:0;background:#ccc;border-radius:22px;transition:.3s}"+
".slider:before{position:absolute;content:\"\";height:16px;width:16px;left:3px;bottom:3px;background:white;border-radius:50%;transition:.3s}"+
"input:checked+.slider{background:#4caf50}"+
"input:checked+.slider:before{transform:translateX(18px)}"+
".btn-choose{background:#f1f3f4;border:1px solid #dadce0;border-radius:6px;padding:8px 16px;font-size:14px;font-weight:500;color:#444;cursor:pointer;white-space:nowrap;transition:background 0.2s,border-color 0.2s;margin-left:auto}"+
".btn-choose:hover{background:#e8eaed;border-color:#b0b8c0}"+
".file-info-card{margin-top:10px;padding:12px 14px;background:#f8f9fa;border:1px solid #e0e0e0;border-radius:8px;display:flex;align-items:center;gap:10px;font-size:13px;color:#444;transition:all 0.2s}"+
".file-info-card .file-icon-display{font-size:28px;flex-shrink:0}"+
".file-details{flex:1;min-width:0}"+
".file-details .file-name-display{font-weight:600;color:#222;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"+
".file-details .file-meta{color:#666;margin-top:2px}"+
".file-meta span{margin-right:10px}"+
".upload-btn-container{margin-top:12px}"+
".upload-btn{width:100%;background:#4caf50;color:white;border:none;padding:12px 20px;border-radius:8px;font-weight:600;font-size:15px;cursor:pointer;transition:background 0.2s}"+
".upload-btn:hover{background:#43a047}"+
".upload-btn:disabled{background:#9e9e9e;color:white;cursor:not-allowed}"+
".folder-btn-container{margin-top:12px}"+
".btn-new-folder{width:100%;display:flex;align-items:center;justify-content:center;background:#f1f3f4;border:1px solid #dadce0;border-radius:8px;padding:12px 20px;font-size:15px;font-weight:500;color:#333;cursor:pointer;transition:background 0.2s,box-shadow 0.2s}"+
".btn-new-folder:hover{background:#e8eaed;box-shadow:0 1px 4px rgba(0,0,0,0.08)}"+
".btn-new-folder:disabled{background:#9e9e9e;color:white;cursor:not-allowed}"+
".footer{margin-top:20px;padding-top:15px;border-top:1px solid #e9ecef;color:#888;font-size:clamp(0.7rem,1.2vw,0.8rem);text-align:center;user-select:text}"+
"#myFile{display:none}"+
".more-row{text-align:center;padding:8px 0}"+
".more-row a{color:#1a73e8;cursor:pointer;text-decoration:none;font-weight:500}"+
".more-row a:hover{text-decoration:underline}"+
"@media(max-width:480px){.container{padding:10px}.name{max-width:100px}th,td{padding:6px 4px}.actions button{padding:2px 4px;font-size:10px}.upload-form{flex-direction:column;align-items:stretch}.upload-form input[type='file']{max-width:none}}"+
"@media(min-width:1600px){.container{max-width:1400px;padding:30px}body{font-size:1.1rem}}"+
"@media (prefers-color-scheme: dark) {"+
"body{background:#1a1a1a;color:#ddd}"+
".container{background:#2a2a2a;box-shadow:0 2px 12px rgba(0,0,0,0.4)}"+
"th,td{border-bottom:1px solid #444}"+
"th{background:#333;color:#ccc}"+
"tr:hover{background:#333}"+
".name a{color:#8ab4f8}"+
".size{color:#bbb}"+
"td button{background:#3a3a3a;border-color:#555;color:#ddd}"+
"td button:hover{background:#4a4a4a}"+
".upload-section{border-color:#555}"+
".chunk-toggle{color:#ccc}"+
".btn-choose{background:#3a3a3a;border-color:#555;color:#ddd}"+
".btn-choose:hover{background:#4a4a4a;border-color:#666}"+
".file-info-card{background:#2a2a2a;border-color:#444}"+
".file-details .file-name-display{color:#ddd}"+
".file-details .file-meta{color:#aaa}"+
".upload-btn{background:#2e7d32}"+
".upload-btn:hover{background:#1b5e20}"+
".upload-btn:disabled{background:#555;color:#ddd;cursor:not-allowed}"+
".btn-new-folder{background:#3a3a3a;border-color:#555;color:#ddd}"+
".btn-new-folder:hover{background:#4a4a4a}"+
".btn-new-folder:disabled{background:#555;color:#ddd;cursor:not-allowed}"+
".footer{border-top:1px solid #444;color:#aaa}"+
"}";

    public static final String ERROR_404_CSS =
"*{margin:0;padding:0;box-sizing:border-box}"+
"body{font-family:system-ui,sans-serif;background:#f5f5f5;min-height:100vh;display:flex;justify-content:center;align-items:center;padding:20px}"+
".error-card{background:white;border-radius:16px;box-shadow:0 4px 20px rgba(0,0,0,0.08);max-width:400px;width:100%;aspect-ratio:1/1;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;padding:32px;border:1px solid #eee}"+
".error-card h1{font-size:clamp(4rem,15vw,7rem);font-weight:700;color:#e53935;margin-bottom:8px}"+
".error-card .not-found-text{font-size:1.25rem;font-weight:600;letter-spacing:2px;color:#555;margin-bottom:32px;text-transform:uppercase}"+
".error-card .home-link{display:inline-block;background:#1976d2;color:white;text-decoration:none;padding:12px 28px;border-radius:30px;font-weight:500}"+
".error-card .home-link:hover{background:#1565c0}"+
"@media(max-width:480px){.error-card{aspect-ratio:auto;min-height:360px;padding:24px}}"+
"@media (prefers-color-scheme: dark) {"+
"body{background:#1a1a1a;color:#ddd}"+
".error-card{background:#2a2a2a;border-color:#444}"+
".error-card .not-found-text{color:#aaa}"+
".error-card .home-link{background:#8ab4f8;color:#000}"+
".error-card .home-link:hover{background:#1565c0;color:#fff}"+
"}";
}