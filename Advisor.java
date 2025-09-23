// Advisor.java

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Advisor {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RootHandler());
        server.setExecutor(null);
        System.out.println("Advisor server running at http://localhost:" + port);
        server.start();
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = PAGE_HTML.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }

    static final String PAGE_HTML = """
<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>STT 화면</title>
<style>
  :root{
    --gap:24px;
    --card-pad:14px 16px;
  }
  *{box-sizing:border-box}
  body{margin:0;font-family:Arial, sans-serif;background:#fff;color:#333}
  .wrap{max-width:1280px;margin:0 auto;padding:24px}

  .grid{
    display:grid;
    grid-template-columns:repeat(3, 1fr);
    grid-template-rows:repeat(2, minmax(0, 1fr));
    gap:var(--gap);
    height:calc(100vh - 120px);
  }

  @media (max-width:1024px){
    .grid{
      grid-template-columns:1fr;
      grid-template-rows:auto;
      height:auto;
    }
  }

  .card{
    border:1px solid #ddd;border-radius:12px;background:#fff;
    box-shadow:0 4px 12px rgba(0,0,0,.05);
  }
  .card h2{
    margin:0;padding:var(--card-pad);font-size:16px;font-weight:600;color:#000;
    border-bottom:1px solid #eee;background:#f9f9f9
  }
  .fill{display:flex;flex-direction:column;min-height:0}
  .fill .body{flex:1;min-height:0;padding:var(--card-pad);display:flex;flex-direction:column;gap:12px}

  /* STT(좌측 1열 전체 차지) */
  .stt{grid-column:1;grid-row:1 / span 2}
  .stt .stt-controls{display:flex;gap:10px;flex-wrap:wrap;align-items:center}
  .file{position:relative;display:inline-flex;align-items:center;gap:10px;padding:8px 12px;border:1px dashed #bbb;border-radius:8px;background:#fafafa;cursor:pointer}
  .file input{position:absolute;inset:0;opacity:0;cursor:pointer}
  .btn{height:38px;padding:0 16px;border-radius:8px;border:none;
       background:linear-gradient(180deg,#2d8cff,#0062cc);color:#fff;font-weight:700;cursor:pointer;
       box-shadow:0 4px 12px rgba(0,98,204,.3)}
  .btn:focus{outline:none;box-shadow:0 0 0 3px rgba(0,98,204,.4)}

  .chat{flex:1;min-height:0;display:flex;flex-direction:column;gap:10px;margin-top:8px;
        border:1px solid #ddd;border-radius:8px;padding:12px;overflow:auto;background:#f5f5f5}
  .msg{max-width:80%;padding:8px 10px;border-radius:8px;line-height:1.45}
  .msg.user{align-self:flex-end;background:#d1e7ff}
  .msg.bot{align-self:flex-start;background:#eee}
  .small{display:block;margin-top:4px;font-size:12px;color:#555}

  textarea{
    width:100%;flex:1;min-height:0;resize:none;padding:10px;border-radius:8px;
    color:#333;background:#f5f5f5;border:1px solid #ccc;outline:none
  }

  .col2-row1{grid-column:2;grid-row:1}
  .col2-row2{grid-column:2;grid-row:2}
  .col3-row1{grid-column:3;grid-row:1}
  .col3-row2{grid-column:3;grid-row:2}
</style>
</head>
<body>
  <div class="wrap">
    <div class="grid">
      <!-- Left: STT -->
      <section class="card fill stt">
        <h2>대화</h2>
        <div class="body">
          <div class="stt-controls">
            <label class="file" id="fileLabel">
              <input id="file" type="file" accept=".wav,audio/wav"/>
              <span>파일 선택</span>
              <span id="fileName" style="font-size:12px;color:#555">선택 안됨</span>
            </label>
            <button class="btn" id="convertBtn">변환</button>
          </div>
          <div class="chat" id="chat">
            <div class="msg bot">안녕하세요! STT는 아직 연결 전입니다.<span class="small">시스템</span></div>
          </div>
        </div>
      </section>

      <!-- Middle column (2열): 추천키워드 ↑, 추천 상담지식 ↓ -->
      <section class="card fill col2-row1">
        <h2>추천키워드</h2>
        <div class="body"><textarea id="keywords" readonly></textarea></div>
      </section>
      <section class="card fill col2-row2">
        <h2>추천 상담지식</h2>
        <div class="body"><textarea id="knowledge" readonly></textarea></div>
      </section>

      <!-- Right column (3열): 상담 요약 ↑, 추천 상담유형 ↓ -->
      <section class="card fill col3-row1">
        <h2>상담 요약</h2>
        <div class="body"><textarea id="summary" readonly></textarea></div>
      </section>
      <section class="card fill col3-row2">
        <h2>추천 상담유형</h2>
        <div class="body"><textarea id="types" readonly></textarea></div>
      </section>
    </div>
  </div>

  <script>
    const chat = document.getElementById('chat');
    const file = document.getElementById('file');
    const fileName = document.getElementById('fileName');
    const convertBtn = document.getElementById('convertBtn');

    file.addEventListener('change', () => {
      fileName.textContent = file.files?.[0]?.name || '선택 안됨';
      if(file.files?.[0]) addMsg('파일 업로드: ' + file.files[0].name, 'user');
    });

    convertBtn.addEventListener('click', () => {
      if(!file.files || !file.files[0]) return addMsg('파일을 먼저 선택하세요.', 'bot');
      setTimeout(() => {
        addMsg('이건 데모 결과입니다. (권한 미설정, MOCK)', 'bot');
        document.getElementById('keywords').value  = "키워드(데모): 이건, 데모, 결과입니다., 권한, 미설정, MOCK";
        document.getElementById('summary').value   = "이건 데모 결과입니다. (권한 미설정, MOCK)";
        document.getElementById('knowledge').value = "";
        document.getElementById('types').value     = "";
      }, 300);
    });

    function addMsg(text, role){
      const el = document.createElement('div');
      el.className = 'msg ' + (role === 'user' ? 'user' : 'bot');
      el.innerHTML = text + '<span class="small">' + (role === 'user' ? '나' : '시스템') + '</span>';
      chat.appendChild(el);
      chat.scrollTop = chat.scrollHeight;
    }
  </script>
</body>
</html>
""";
}
