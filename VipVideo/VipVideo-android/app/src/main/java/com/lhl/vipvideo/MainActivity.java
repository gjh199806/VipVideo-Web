package com.lhl.vipvideo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int STORAGE_PERMISSION_REQUEST = 41;

    private final List<Site> sites = new ArrayList<>();
    private final List<Parser> parsers = new ArrayList<>();

    private LinearLayout root;
    private WebView webView;
    private TextView titleView;
    private ProgressBar progressBar;
    private Button vipButton;
    private Site selectedSite;
    private String lastOriginalUrl = "";
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadConfig();
        buildUi();
        if (sites.isEmpty()) {
            Toast.makeText(this, "站点配置为空", Toast.LENGTH_LONG).show();
        } else {
            openSite(sites.get(0));
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        }
    }

    private void loadConfig() {
        try (InputStream input = getAssets().open("vlist.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            JSONArray platformList = root.optJSONArray("platformlist");
            if (platformList != null) {
                for (int i = 0; i < platformList.length(); i++) {
                    JSONObject item = platformList.getJSONObject(i);
                    sites.add(new Site(
                            item.optString("name", "未命名"),
                            item.optString("url", ""),
                            item.optString("category", "other"),
                            item.optInt("canvip", 0) == 1));
                }
            }
            JSONArray parserList = root.optJSONArray("list");
            if (parserList != null) {
                for (int i = 0; i < parserList.length(); i++) {
                    JSONObject item = parserList.getJSONObject(i);
                    parsers.add(new Parser(item.optString("name", "线路"),
                            item.optString("url", "")));
                }
            }
        } catch (Exception error) {
            Toast.makeText(this, "读取配置失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(7), dp(14), 0);

        titleView = new TextView(this);
        titleView.setText("VIP Video");
        titleView.setTextColor(Color.rgb(35, 35, 40));
        titleView.setTextSize(15);
        titleView.setSingleLine(true);
        header.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.INVISIBLE);
        header.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        root.addView(header);

        webView = new WebView(this);
        configureWebView();
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(buildToolbar());
        setContentView(root);
    }

    private View buildToolbar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.rgb(248, 247, 251));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));

        bar.addView(actionButton("后退", view -> {
            if (webView.canGoBack()) webView.goBack();
        }));
        bar.addView(actionButton("前进", view -> {
            if (webView.canGoForward()) webView.goForward();
        }));
        bar.addView(actionButton("首页", view -> {
            if (selectedSite != null) openSite(selectedSite);
        }));
        bar.addView(actionButton("平台", view -> showSiteDialog()));
        vipButton = actionButton("VIP线路", view -> showParserDialog());
        vipButton.setEnabled(false);
        bar.addView(vipButton);
        bar.addView(actionButton("浏览器", view -> openExternal(webView.getUrl())));

        scroll.addView(bar);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return scroll;
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(dp(72));
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)));
        return button;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportMultipleWindows(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                progressBar.setVisibility(View.VISIBLE);
                if (!isParserUrl(url)) lastOriginalUrl = url;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String title = view.getTitle();
                titleView.setText(title == null || title.trim().isEmpty()
                        ? (selectedSite == null ? "VIP Video" : selectedSite.name)
                        : title);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean dialog,
                                          boolean userGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView ignored,
                                                            WebResourceRequest request) {
                        webView.loadUrl(request.getUrl().toString());
                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView ignored, String url) {
                        webView.loadUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Do not grant camera or microphone access to arbitrary third-party pages.
                runOnUiThread(request::deny);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                                     JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.setVisibility(View.GONE);
                ((ViewGroup) getWindow().getDecorView()).addView(view,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mimeType, size) ->
                enqueueDownload(url, userAgent, disposition, mimeType));
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme)) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开此链接", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void openSite(Site site) {
        selectedSite = site;
        lastOriginalUrl = site.url;
        vipButton.setEnabled(site.canVip);
        titleView.setText(site.name);
        webView.loadUrl(site.url);
    }

    private void showSiteDialog() {
        String[] items = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            items[i] = categoryName(site.category) + " · " + site.name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择平台")
                .setItems(items, (dialog, index) -> openSite(sites.get(index)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showParserDialog() {
        if (selectedSite == null || !selectedSite.canVip) {
            Toast.makeText(this, "当前平台未启用线路切换", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[parsers.size()];
        for (int i = 0; i < parsers.size(); i++) {
            String name = parsers.get(i).name;
            items[i] = name.startsWith("原地址") ? "原地址" : name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择播放线路")
                .setItems(items, (dialog, index) -> applyParser(parsers.get(index)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyParser(Parser parser) {
        String original = lastOriginalUrl;
        if (original == null || original.isEmpty()) original = webView.getUrl();
        if (original == null || original.isEmpty()) return;
        webView.loadUrl(parser.prefix + original);
    }

    private boolean isParserUrl(String url) {
        if (url == null) return false;
        for (Parser parser : parsers) {
            if (!parser.prefix.isEmpty() && url.startsWith(parser.prefix)) return true;
        }
        return false;
    }

    private void openExternal(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void enqueueDownload(String url, String userAgent,
                                 String disposition, String mimeType) {
        try {
            String fileName = android.webkit.URLUtil.guessFileName(url, disposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.addRequestHeader("User-Agent", userAgent);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle(fileName);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            if (mimeType != null) request.setMimeType(mimeType);
            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "已开始下载：" + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            openExternal(url);
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        ((ViewGroup) customView.getParent()).removeView(customView);
        customView = null;
        root.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    private String categoryName(String category) {
        if ("tv".equals(category)) return "电视";
        if ("video".equals(category)) return "视频";
        if ("music".equals(category)) return "音乐";
        if ("novel".equals(category)) return "小说";
        if ("comic".equals(category)) return "漫画";
        return "其他";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage("退出 VIP Video？")
                    .setPositiveButton("退出", (dialog, which) -> finish())
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private static final class Site {
        final String name;
        final String url;
        final String category;
        final boolean canVip;

        Site(String name, String url, String category, boolean canVip) {
            this.name = name;
            this.url = url;
            this.category = category;
            this.canVip = canVip;
        }
    }

    private static final class Parser {
        final String name;
        final String prefix;

        Parser(String name, String prefix) {
            this.name = name;
            this.prefix = prefix;
        }
    }
}
