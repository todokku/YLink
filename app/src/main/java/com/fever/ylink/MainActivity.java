package com.fever.ylink;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView inpURL = null;
    private TextView statusBar = null;
    private ClipboardManager clipboard = null;

    private WebView webView = null;

    private Integer callbackIndex = 0;
    private List<Object[]> callbackStack = new ArrayList();

    private Boolean isReady = false;
    private String onReadyUrl = null;

    interface MyCallback {
        void callbackCall(JSONObject json);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        inpURL = (TextView) findViewById(R.id.inpURL);
        Button btnClear = (Button) findViewById(R.id.btnClear);
        Button btnPaste = (Button) findViewById(R.id.btnPaste);
        Button btnGetLink = (Button) findViewById(R.id.btnGetLink);
        statusBar = (TextView) findViewById(R.id.statusBar);
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                inpURL.setText("");
                statusBar.setText("");
            }
        });
        btnPaste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) {
                    return;
                }
                Integer index = 0;
                ClipData.Item item = clip.getItemAt(index);
                String text = item.getText().toString();
                inpURL.setText(text);
            }
        });
        btnGetLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getUrlLinks(inpURL.getText().toString());
            }
        });

        initWebView();
    }

    private void onReady() {
        isReady = true;
        Log.d("myApp", "onReady");
        writeInStatus("Ready!");

        if (onReadyUrl != null) {
            getUrlLinks(onReadyUrl);
            onReadyUrl = null;
        }
    }

    private void getUrlLinks(String url) {
        if (!isReady) {
            Log.d("myApp", "stackUrl: " + url);
            onReadyUrl = url;
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put("action", "getVideoLink");
            message.put("url", url);
            bridgeSendMessage(message);
        } catch (JSONException e) {
            Log.e("onReady", e.toString());
        }
    }

    private void initWebView() {
        webView = (WebView) findViewById(R.id.webView);
        webView.addJavascriptInterface(new JsObject(), "monoBridge");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setWebContentsDebuggingEnabled(true);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setDomStorageEnabled(true);

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class MyResponse {
        private String callbackId = null;
        private MyResponse(JSONObject msg) throws JSONException {
            this.callbackId = msg.getString("callbackId");
        }
        public void responseCall(JSONObject json) {
            JSONObject message = new JSONObject();
            try {
                message.put("mono", true);
                message.put("data", json);
                message.put("responseId", callbackId);
            } catch (JSONException e) {
                Log.e("setMsg", e.toString());
            }
            _bridgeSendMessage(message.toString());
        }
    }

    private class OnMessage {
        public void ping(JSONObject data, MyResponse response) throws JSONException {
            JSONObject responseMsg = new JSONObject();
            responseMsg.put("action", "pong");
            response.responseCall(responseMsg);
        }
        public void ready(JSONObject data, MyResponse response) {
            onReady();
        }
        public void setStatus(JSONObject data, MyResponse response) throws JSONException {
            writeInStatus(data.getString("text"));
        }
        public void openUrl(JSONObject data, MyResponse response) throws JSONException {
            String url = data.getString("url");
            String mime = "video/*.*";
            /*
            // todo: fix me!
            if (data.has("mime")) {
                mime = data.getString("mime");
            }
            */

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(Uri.parse(url), mime);
            startActivity(intent.createChooser(intent, "Chose application"));
        }
    }

    private OnMessage onMessage = new OnMessage();

    public class JsObject {
        @JavascriptInterface
        public void sendMessage(String message) throws JSONException {
            JSONObject jsonObject = new JSONObject(message);
            JSONObject data = jsonObject.getJSONObject("data");

            if (jsonObject.has("responseId")) {
                Integer id = jsonObject.getInt("responseId");
                for (int i = 0; i < callbackStack.size(); i++) {
                    Object[] item = callbackStack.get(i);
                    Integer cbId = (Integer)item[0];
                    if (cbId.equals(id)) {
                        MyCallback cb = (MyCallback)item[1];
                        cb.callbackCall(data);
                        callbackStack.remove(i);
                        break;
                    }
                }
            } else {
                MyResponse response = null;
                if (jsonObject.has("callbackId")) {
                    response = new MyResponse(jsonObject);
                }

                if (data.get("action").equals("ping")) {
                    onMessage.ping(data, response);
                } else
                if (data.get("action").equals("ready")) {
                    onMessage.ready(data, response);
                } else
                if (data.get("action").equals("setStatus")) {
                    onMessage.setStatus(data, response);
                } else
                if (data.get("action").equals("openUrl")) {
                    onMessage.openUrl(data, response);
                }
            }
        }
    }

    private  void _bridgeSendMessage(final String message) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                String script = "(function(message){" +
                        "window.dispatchEvent(new CustomEvent(\"monoMessage\",{detail:'<'+JSON.stringify(message)}));" +
                        "})("+message+");";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript(script, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {

                        }
                    });
                } else {
                    webView.loadUrl("javascript:" + script);
                }
            }
        });
    }

    private void bridgeSendMessage(final JSONObject data) throws JSONException {
        bridgeSendMessage(data, null);
    }

    private void bridgeSendMessage(final JSONObject data, MyCallback callback) throws JSONException {
        JSONObject message = new JSONObject();

        message.put("mono", true);
        message.put("data", data);

        if (callback != null) {
            Integer id = callbackIndex++;
            Object[] item = new Object[]{id, callback};
            callbackStack.add(item);
            message.put("hasCallback", true);
            message.put("callbackId", id.toString());
        } else {
            message.put("hasCallback", false);
        }

        _bridgeSendMessage(message.toString());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("myApp", "onResume");

        Intent intent = getIntent();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent); // Handle text being sent
            }
        }
    }

    private void handleSendText(Intent intent) {
        final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        intent.removeExtra(Intent.EXTRA_TEXT);

        Log.d("myApp", "handleSendText " + sharedText);

        if (sharedText != null) {
            inpURL.setText(sharedText);
            getUrlLinks(sharedText);
        }
    }

    private void writeInStatus(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                statusBar.setText(text);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();

        return true;
    }

}