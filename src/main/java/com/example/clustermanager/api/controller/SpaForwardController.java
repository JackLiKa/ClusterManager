package com.example.clustermanager.api.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/dashboard",
            "/topology",
            "/messages",
            "/operations"
    })
    public String forward() {
        return "forward:/index.html";
    }

    @GetMapping(value = "/index.html", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> index() throws Exception {
        ClassPathResource packagedIndex = new ClassPathResource("static/index.html");
        if (packagedIndex.exists()) {
            return ResponseEntity.ok(new String(packagedIndex.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
        Path devIndex = Path.of("frontend", "index.html").toAbsolutePath().normalize();
        if (Files.exists(devIndex)) {
            return ResponseEntity.ok(Files.readString(devIndex, StandardCharsets.UTF_8));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/guide", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> guide() throws Exception {
        ClassPathResource manual = new ClassPathResource("manual.md");
        if (!manual.exists()) {
            return ResponseEntity.notFound().build();
        }
        String markdown = new String(manual.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String html = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>Cluster Manager Guide</title>
                  <style>
                    body { margin: 0; font-family: "Segoe UI", "PingFang SC", sans-serif; background: #f4f7f1; color: #173126; }
                    main { max-width: 920px; margin: 40px auto; padding: 32px; background: rgba(255,255,255,0.92); border-radius: 24px; box-shadow: 0 20px 60px rgba(42,73,57,0.12); }
                    h1 { font-size: 34px; margin: 0 0 18px; }
                    pre { padding: 14px 16px; overflow: auto; white-space: pre-wrap; word-break: break-word; line-height: 1.75; color: #3f5b4f; background: #f0f5ef; border-radius: 10px; font-family: Consolas, monospace; }
                  </style>
                </head>
                <body>
                  <main><h1>Cluster Manager Guide</h1><pre>%s</pre></main>
                </body>
                </html>
                """.formatted(escapeHtml(markdown));
        return ResponseEntity.ok(html);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
