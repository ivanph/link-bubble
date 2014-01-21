package com.linkbubble.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.graphics.Canvas;
import android.widget.Toast;
import com.linkbubble.util.ActionItem;
import com.linkbubble.Config;
import com.linkbubble.MainApplication;
import com.linkbubble.MainController;
import com.linkbubble.R;
import com.linkbubble.Settings;
import com.linkbubble.util.Util;
import com.linkbubble.util.YouTubeEmbedHelper;
import org.mozilla.gecko.favicons.Favicons;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Created by gw on 19/08/13.
 */
public class ContentView extends FrameLayout {

    private static final String TAG = "UrlLoad";

    private WebView mWebView;
    private CondensedTextView mTitleTextView;
    private CondensedTextView mUrlTextView;
    private ContentViewButton mShareButton;
    private ContentViewButton mReloadButton;
    private OpenInAppButton mOpenInAppButton;
    private OpenEmbedButton mOpenEmbedButton;
    private ContentViewButton mOverflowButton;
    private LinearLayout mToolbarLayout;
    private EventHandler mEventHandler;
    private Context mContext;
    private URL mUrl;
    private boolean mPageFinishedLoading;

    private List<AppForUrl> mAppsForUrl = new ArrayList<AppForUrl>();
    private List<ResolveInfo> mTempAppsForUrl = new ArrayList<ResolveInfo>();
    private YouTubeEmbedHelper mYouTubeEmbedHelper;
    private int mCheckForEmbedsCount;
    private PopupMenu mOverflowPopupMenu;
    private AlertDialog mLongPressAlertDialog;
    private long mStartTime;
    private int mHeaderHeight;
    private Path mTempPath = new Path();
    private int mLoadCount = 0;
    private String mCurrentLoadedUrl;
    private boolean mLoadingPrev;

    private static Paint sIndicatorPaint;
    private static Paint sBorderPaint;

    private Stack<String> mUrlHistory = new Stack<String>();

    private static final String JS_VARIABLE = "LinkBubble";
    private static final String JS_EMBED = "javascript:(function() {\n" +
            "    var elems = document.getElementsByTagName('*'), i;\n" +
            "    var resultArray = null;\n" +
            "    var resultCount = 0;\n" +
            "    for (i in elems) {\n" +
            "       var elem = elems[i];\n" +
            "       if (elem.src != null && elem.src.indexOf(\"" + Config.YOUTUBE_EMBED_PREFIX + "\") != -1) {\n" +
                //"           //console.log(\"found embed: \" + elem.src);\n" +
            "           if (resultArray == null) {\n" +
            "               resultArray = new Array();\n" +
                //"           console.log(\"allocate array\");\n" +
            "           }\n" +
            "           resultArray[resultCount] = elem.src;\n" +
            "           resultCount++;\n" +
            "       }\n" +
            "    }\n" +
            "    if (resultCount > 0) {\n" +
            "       " + JS_VARIABLE + ".onEmbeds(resultArray.toString());\n" +
            "    }\n" +
            "})();";

    public ContentView(Context context) {
        this(context, null);
    }

    public ContentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    static class AppForUrl {
        ResolveInfo mResolveInfo;
        URL mUrl;
        Drawable mIcon;

        AppForUrl(ResolveInfo resolveInfo, URL url) {
            mResolveInfo = resolveInfo;
            mUrl = url;
        }

        Drawable getIcon(Context context) {
            if (mIcon == null) {
                // TODO: Handle OutOfMemory error
                mIcon = mResolveInfo.loadIcon(context.getPackageManager());
            }

            return mIcon;
        }
    };

    public static class PageLoadInfo {
        String url;
        String mHost;
        String title;
    }

    public interface EventHandler {
        public void onDestroyBubble();
        public void onMinimizeBubbles();
        public void onPageLoading(URL url);
        public void onProgressChanged(int progress);
        public void onPageLoaded(PageLoadInfo info);
        public void onReceivedIcon(Bitmap bitmap);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (isInEditMode()) {
            return;
        }

        float centerX = Config.mScreenCenterX;
        float indicatorEndY = 2.f;
        float indicatorStartX = centerX - mHeaderHeight + indicatorEndY;
        float indicatorEndX = centerX + mHeaderHeight - indicatorEndY;

        mTempPath.reset();
        mTempPath.moveTo(indicatorStartX, mHeaderHeight);
        mTempPath.lineTo(centerX, indicatorEndY);
        mTempPath.lineTo(indicatorEndX, mHeaderHeight);
        canvas.drawPath(mTempPath, sIndicatorPaint);

        canvas.drawLine(indicatorEndY, mHeaderHeight, indicatorStartX, mHeaderHeight, sBorderPaint);
        canvas.drawLine(indicatorStartX, mHeaderHeight, centerX, 0, sBorderPaint);
        canvas.drawLine(centerX, indicatorEndY, indicatorEndX, mHeaderHeight, sBorderPaint);
        canvas.drawLine(indicatorEndX, mHeaderHeight, Config.mScreenWidth, mHeaderHeight, sBorderPaint);
    }

    public void destroy() {
        removeView(mWebView);
        mWebView.destroy();
    }

    public void updateIncognitoMode(boolean incognito) {
        if (incognito) {
            mWebView.getSettings().setCacheMode(mWebView.getSettings().LOAD_NO_CACHE);
            mWebView.getSettings().setAppCacheEnabled(false);
            mWebView.clearHistory();
            mWebView.clearCache(true);

            mWebView.clearFormData();
            mWebView.getSettings().setSavePassword(false);
            mWebView.getSettings().setSaveFormData(false);
        } else {
            mWebView.getSettings().setCacheMode(mWebView.getSettings().LOAD_DEFAULT);
            mWebView.getSettings().setAppCacheEnabled(true);

            mWebView.getSettings().setSavePassword(true);
            mWebView.getSettings().setSaveFormData(true);
        }
    }

    private boolean isValidUrl(URL url) {
        if (url == null) {
            return false;
        }

        boolean isValid = true;

        String [] urlBlacklist = { "t.co", "goo.gl", "bit.ly" };

        String hostName = url.getHost();

        for (int i=0 ; i < urlBlacklist.length ; ++i) {
            if (hostName.equalsIgnoreCase(urlBlacklist[i])) {
                isValid = false;
                break;
            }
        }

        return isValid;
    }

    private void showSelectShareMethod(final String urlAsString, final boolean closeBubbleOnShare) {

        AlertDialog alertDialog = ActionItem.getShareAlert(mContext, new ActionItem.OnActionItemSelectedListener() {
            @Override
            public void onSelected(ActionItem actionItem) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.setClassName(actionItem.mPackageName, actionItem.mActivityClassName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(Intent.EXTRA_TEXT, urlAsString);
                mContext.startActivity(intent);

                if (closeBubbleOnShare) {
                    mEventHandler.onDestroyBubble();
                }
            }
        });
        alertDialog.show();
    }

    void configure(String urlAsString, long startTime, EventHandler eh) throws MalformedURLException {
        if (sIndicatorPaint == null) {
            sIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            sIndicatorPaint.setColor(getResources().getColor(R.color.content_toolbar_background));
        }

        if (sBorderPaint == null) {
            sBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            sBorderPaint.setColor(getResources().getColor(R.color.bubble_border));
        }

        mUrl = new URL(urlAsString);

        mHeaderHeight = getResources().getDimensionPixelSize(R.dimen.toolbar_header);

        mWebView = (WebView) findViewById(R.id.webView);
        mToolbarLayout = (LinearLayout) findViewById(R.id.content_toolbar);
        mTitleTextView = (CondensedTextView) findViewById(R.id.title_text);
        mUrlTextView = (CondensedTextView) findViewById(R.id.url_text);

        View textContainer = findViewById(R.id.content_text_container);
        textContainer.setOnTouchListener(new OnSwipeTouchListener() {
            public void onSwipeRight() {
                MainController.get().showPreviousBubble();
            }
            public void onSwipeLeft() {
                MainController.get().showNextBubble();
            }
        });

        mShareButton = (ContentViewButton)findViewById(R.id.share_button);
        mShareButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_share));
        mShareButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showSelectShareMethod(mUrl.toString(), true);
            }
        });

        mOpenInAppButton = (OpenInAppButton)findViewById(R.id.open_in_app_button);
        mOpenInAppButton.setOnOpenInAppClickListener(new OpenInAppButton.OnOpenInAppClickListener() {

            @Override
            public void onAppOpened() {
                mEventHandler.onDestroyBubble();
            }

        });

        mOpenEmbedButton = (OpenEmbedButton)findViewById(R.id.open_embed_button);
        mOpenEmbedButton.setOnOpenEmbedClickListener(new OpenEmbedButton.OnOpenEmbedClickListener() {

            @Override
            public void onYouTubeEmbedOpened() {

            }
        });

        mReloadButton = (ContentViewButton)findViewById(R.id.reload_button);
        mReloadButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_reload));
        mReloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mReloadButton.setVisibility(GONE);
                mWebView.reload();
            }
        });

        mOverflowButton = (ContentViewButton)mToolbarLayout.findViewById(R.id.overflow_button);
        mOverflowButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_overflow_round));
        mOverflowButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mOverflowPopupMenu = new PopupMenu(mContext, mOverflowButton);
                Resources resources = mContext.getResources();
                mOverflowPopupMenu.getMenu().add(Menu.NONE, R.id.item_upgrade_to_pro, Menu.NONE,
                        resources.getString(R.string.action_upgrade_to_pro));
                mOverflowPopupMenu.getMenu().add(Menu.NONE, R.id.item_reload_page, Menu.NONE,
                        resources.getString(R.string.action_reload_page));
                String defaultBrowserLabel = Settings.get().getDefaultBrowserLabel();
                if (defaultBrowserLabel != null) {
                    mOverflowPopupMenu.getMenu().add(Menu.NONE, R.id.item_open_in_browser, Menu.NONE,
                            String.format(resources.getString(R.string.action_open_in_browser), defaultBrowserLabel));
                }
                mOverflowPopupMenu.getMenu().add(Menu.NONE, R.id.item_settings, Menu.NONE,
                        resources.getString(R.string.action_settings));
                mOverflowPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.item_upgrade_to_pro: {
                                Intent intent = Config.getStoreIntent(mContext, Config.STORE_PRO_URL);
                                if (intent != null) {
                                    mContext.startActivity(intent);
                                    mEventHandler.onMinimizeBubbles();
                                }
                                break;
                            }

                            case R.id.item_reload_page: {
                                if (mYouTubeEmbedHelper != null) {
                                    mYouTubeEmbedHelper.clear();
                                }
                                mEventHandler.onPageLoading(mUrl);
                                mWebView.stopLoading();
                                mWebView.reload();
                                String urlAsString = mUrl.toString();
                                updateAppsForUrl(mUrl);
                                configureOpenInAppButton();
                                configureOpenEmbedButton();
                                Log.d(TAG, "reload url: " + urlAsString);
                                mStartTime = System.currentTimeMillis();
                                mTitleTextView.setText(R.string.loading);
                                mUrlTextView.setText(urlAsString.replace("http://", ""));
                                break;
                            }

                            case R.id.item_open_in_browser: {
                                openInBrowser(mUrl.toString());
                                break;
                            }

                            case R.id.item_settings: {
                                Intent intent = new Intent(mContext, SettingsActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                mContext.startActivity(intent);
                                mEventHandler.onMinimizeBubbles();
                                break;
                            }
                        }
                        mOverflowPopupMenu = null;
                        return false;
                    }
                });
                mOverflowPopupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                    @Override
                    public void onDismiss(PopupMenu menu) {
                        if (mOverflowPopupMenu == menu) {
                            mOverflowPopupMenu = null;
                        }
                    }
                });
                mOverflowPopupMenu.show();
            }
        });

        mContext = getContext();
        mEventHandler = eh;
        mPageFinishedLoading = false;

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        mWebView.setLongClickable(true);
        mWebView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult hitTestResult = mWebView.getHitTestResult();
                Log.d(TAG, "onLongClick type: " + hitTestResult.getType());
                switch (hitTestResult.getType()) {
                    case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                    case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE: {
                        final String url = hitTestResult.getExtra();
                        if (url == null) {
                            return false;
                        }

                        onUrlLongClick(url);
                        return true;
                    }

                    case WebView.HitTestResult.UNKNOWN_TYPE:
                    default:
                        String defaultBrowserLabel = Settings.get().getDefaultBrowserLabel();
                        String message;
                        if (defaultBrowserLabel != null) {
                            message = String.format(getResources().getString(R.string.long_press_unsupported_default_browser), defaultBrowserLabel);
                        } else {
                            message = getResources().getString(R.string.long_press_unsupported_no_default_browser);
                        }
                        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                        return false;
                }
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView webView, String title) {
                super.onReceivedTitle(webView, title);
                mTitleTextView.setText(title);
            }

            @Override
            public void onReceivedIcon(WebView webView, Bitmap bitmap) {
                super.onReceivedIcon(webView, bitmap);

                // Only pass this along if the page has finished loading (https://github.com/chrislacy/LinkBubble/issues/155).
                // This is to prevent passing a stale icon along when a redirect has already occurred. This shouldn't cause
                // too many ill-effects, because BitmapView attempts to load host/favicon.ico automatically anyway.
                if (mPageFinishedLoading) {
                    mEventHandler.onReceivedIcon(bitmap);

                    String faviconUrl = Util.getDefaultFaviconUrl(mUrl);
                    Favicons.putFaviconInMemCache(faviconUrl, bitmap);
                }
            }

            @Override
            public void onProgressChanged(WebView webView, int progress) {
                //Log.d(TAG, "onProgressChanged() - progress:" + progress);

                mEventHandler.onProgressChanged(progress);

                // At 60%, the page is more often largely viewable, but waiting for background shite to finish which can
                // take many, many seconds, even on a strong connection. Thus, do a check for embeds now to prevent the button
                // not being updated until 100% is reached, which feels too slow as a user.
                if (progress >= 60) {
                    if (mCheckForEmbedsCount == 0) {
                        mCheckForEmbedsCount = 1;
                        if (mYouTubeEmbedHelper != null) {
                            mYouTubeEmbedHelper.clear();
                        }

                        if (Settings.get().checkForYouTubeEmbeds()) {
                            Log.d(TAG, "onProgressChanged() - checkForYouTubeEmbeds() - progress:" + progress + ", mCheckForEmbedsCount:" + mCheckForEmbedsCount);
                            webView.loadUrl(JS_EMBED);
                        }
                    } else if (mCheckForEmbedsCount == 1 && progress >= 80) {
                        mCheckForEmbedsCount = 2;
                        if (Settings.get().checkForYouTubeEmbeds()) {
                            Log.d(TAG, "onProgressChanged() - checkForYouTubeEmbeds() - progress:" + progress + ", mCheckForEmbedsCount:" + mCheckForEmbedsCount);
                            webView.loadUrl(JS_EMBED);
                        }
                    }
                }
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView wView, String urlAsString) {

                if (mLoadCount == 0) {
                    if (mCurrentLoadedUrl != null && !mLoadingPrev) {
                        mUrlHistory.push(mCurrentLoadedUrl);
                    }
                    mCurrentLoadedUrl = null;
                    mLoadingPrev = false;
                }

                ++mLoadCount;
                updateUrl(urlAsString);

                if (mYouTubeEmbedHelper != null) {
                    mYouTubeEmbedHelper.clear();
                }

                List<ResolveInfo> resolveInfos = Settings.get().getAppsThatHandleUrl(urlAsString);
                updateAppsForUrl(resolveInfos, mUrl);
                if (Settings.get().redirectUrlToBrowser(urlAsString)) {
                    if (openInBrowser(urlAsString)) {
                        String title = String.format(mContext.getString(R.string.link_redirected), Settings.get().getDefaultBrowserLabel());
                        MainApplication.saveUrlInHistory(mContext, null, urlAsString, title);
                        return false;
                    }
                }

                if (Settings.get().getAutoContentDisplayAppRedirect() && resolveInfos != null && resolveInfos.size() > 0) {
                    ResolveInfo resolveInfo = resolveInfos.get(0);
                    if (resolveInfo != Settings.get().mLinkBubbleEntryActivityResolveInfo) {
                        // TODO: Fix to handle multiple apps
                        if (MainApplication.loadResolveInfoIntent(mContext, resolveInfo, urlAsString, mStartTime)) {
                            String title = String.format(mContext.getString(R.string.link_loaded_with_app),
                                                        resolveInfo.loadLabel(mContext.getPackageManager()));
                            MainApplication.saveUrlInHistory(mContext, resolveInfo, urlAsString, title);

                            mEventHandler.onDestroyBubble();
                            return false;
                        }
                    }
                }

                configureOpenInAppButton();
                configureOpenEmbedButton();
                Log.d(TAG, "redirect to url: " + urlAsString);
                mWebView.loadUrl(urlAsString);
                mEventHandler.onPageLoading(mUrl);
                mTitleTextView.setText(R.string.loading);
                mUrlTextView.setText(urlAsString.replace("http://", ""));
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                mEventHandler.onPageLoaded(null);
                mReloadButton.setVisibility(VISIBLE);
                mShareButton.setVisibility(GONE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageStarted(WebView view, String urlAsString, Bitmap favIcon) {
                mPageFinishedLoading = false;

                updateUrl(urlAsString);
                mLoadCount = Math.max(mLoadCount, 1);

                if (mShareButton.getVisibility() == GONE) {
                    mShareButton.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView webView, String urlAsString) {
                super.onPageFinished(webView, urlAsString);
                mPageFinishedLoading = true;
                // NOTE: *don't* call updateUrl() here. Turns out, this function is called after a redirect has occurred.
                // Eg, urlAsString "t.co/xyz" even after the next redirect is starting to load
                if (mUrl != null)
                {
                    updateAppsForUrl(mUrl);
                    configureOpenInAppButton();
                    configureOpenEmbedButton();

                    mTitleTextView.setText(webView.getTitle());
                    mUrlTextView.setText(urlAsString.replace("http://", ""));

                    if (--mLoadCount == 0) {
                        mCurrentLoadedUrl = mUrl.toString();

                        PageLoadInfo pageLoadInfo = new PageLoadInfo();
                        pageLoadInfo.url = urlAsString;
                        pageLoadInfo.mHost = mUrl.getHost();
                        pageLoadInfo.title = webView.getTitle();

                        mEventHandler.onPageLoaded(pageLoadInfo);
                        Log.d(TAG, "onPageFinished() - url: " + urlAsString);

                        if (mStartTime > -1) {
                            Log.d("LoadTime", "Saved " + ((System.currentTimeMillis() - mStartTime) / 1000) + " seconds.");
                            mStartTime = -1;
                        }

                        // Always check again at 100%
                        if (Settings.get().checkForYouTubeEmbeds()) {
                            webView.loadUrl(JS_EMBED);
                            Log.d(TAG, "onPageFinished() - checkForYouTubeEmbeds()");
                        }
                    }
                }
            }
        });

        mWebView.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    WebView webView = (WebView) v;
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK: {
                            if (mUrlHistory.size() == 0) {
                                mEventHandler.onDestroyBubble();
                            } else {
                                webView.stopLoading();
                                String urlBefore = webView.getUrl();

                                String prevUrl = mUrlHistory.pop();
                                mLoadingPrev = true;
                                webView.loadUrl(prevUrl);

                                updateUrl(prevUrl);
                                updateAppsForUrl(mUrl);
                                if (mYouTubeEmbedHelper != null) {
                                    mYouTubeEmbedHelper.clear();
                                }
                                Log.d(TAG, "Go back: " + urlBefore + " -> " + webView.getUrl());
                                configureOpenInAppButton();
                                configureOpenEmbedButton();
                                return true;
                            }
                            break;
                        }
                    }
                }

                return false;
            }
        });

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String urlAsString, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                openInBrowser(urlAsString);
                mEventHandler.onDestroyBubble();
            }
        });

        if (Settings.get().checkForYouTubeEmbeds()) {
            mJSEmbedHandler = new JSEmbedHandler();
            mWebView.addJavascriptInterface(mJSEmbedHandler, JS_VARIABLE);
        }

        updateIncognitoMode(Settings.get().isIncognitoMode());

        updateAppsForUrl(mUrl);
        configureOpenInAppButton();
        configureOpenEmbedButton();
        Log.d(TAG, "load url: " + urlAsString);
        mStartTime = startTime;
        mWebView.loadUrl(urlAsString);
        mEventHandler.onPageLoading(mUrl);
        mTitleTextView.setText(R.string.loading);
        mUrlTextView.setText(urlAsString.replace("http://", ""));
    }

    private void onUrlLongClick(final String urlAsString) {
        Resources resources = mContext.getResources();

        final ArrayList<String> longClickSelections = new ArrayList<String>();

        final String shareLabel = resources.getString(R.string.action_share);
        longClickSelections.add(shareLabel);

        String defaultBrowserLabel = Settings.get().getDefaultBrowserLabel();

        final String leftConsumeBubbleLabel = Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeLeft);
        if (leftConsumeBubbleLabel != null) {
            if (defaultBrowserLabel == null || defaultBrowserLabel.equals(leftConsumeBubbleLabel) == false) {
                longClickSelections.add(leftConsumeBubbleLabel);
            }
        }

        final String rightConsumeBubbleLabel = Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeRight);
        if (rightConsumeBubbleLabel != null) {
            if (defaultBrowserLabel == null || defaultBrowserLabel.equals(rightConsumeBubbleLabel) == false) {
                longClickSelections.add(rightConsumeBubbleLabel);
            }
        }

        Collections.sort(longClickSelections);

        final String openInNewBubbleLabel = resources.getString(R.string.action_open_in_new_bubble);
        longClickSelections.add(0, openInNewBubbleLabel);

        final String openInBrowserLabel = defaultBrowserLabel != null ?
                String.format(resources.getString(R.string.action_open_in_browser), defaultBrowserLabel) : null;
        if (openInBrowserLabel != null) {
            longClickSelections.add(1, openInBrowserLabel);
        }

        ListView listView = new ListView(getContext());
        listView.setAdapter(new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1,
                longClickSelections.toArray(new String[0])));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String string = longClickSelections.get(position);
                if (string.equals(openInNewBubbleLabel)) {
                    MainController.get().onOpenUrl(urlAsString, System.currentTimeMillis());
                } else if (openInBrowserLabel != null && string.equals(openInBrowserLabel)) {
                    openInBrowser(urlAsString);
                } else if (string.equals(shareLabel)) {
                    showSelectShareMethod(urlAsString, false);
                } else if (leftConsumeBubbleLabel != null && string.equals(leftConsumeBubbleLabel)) {
                    MainApplication.handleBubbleAction(mContext, Config.BubbleAction.ConsumeLeft, urlAsString);
                } else if (rightConsumeBubbleLabel != null && string.equals(rightConsumeBubbleLabel)) {
                    MainApplication.handleBubbleAction(mContext, Config.BubbleAction.ConsumeRight, urlAsString);
                }

                if (mLongPressAlertDialog != null) {
                    mLongPressAlertDialog.dismiss();
                }
            }
        });

        mLongPressAlertDialog = new AlertDialog.Builder(getContext()).create();
        mLongPressAlertDialog.setView(listView);
        mLongPressAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mLongPressAlertDialog.show();
    }

    private void configureOpenEmbedButton() {
        if (mOpenEmbedButton.configure(mYouTubeEmbedHelper)) {
            mOpenEmbedButton.invalidate();
        } else {
            mOpenEmbedButton.setVisibility(GONE);
        }
    }

    private void configureOpenInAppButton() {
        if (mOpenInAppButton.configure(mAppsForUrl)) {
            mOpenInAppButton.invalidate();
        } else {
            mOpenInAppButton.setVisibility(GONE);
        }
    }

    private void updateAppsForUrl(URL url) {
        List<ResolveInfo> resolveInfos = Settings.get().getAppsThatHandleUrl(url.toString());
        updateAppsForUrl(resolveInfos, url);
    }

    private void updateAppsForUrl(List<ResolveInfo> resolveInfos, URL url) {
        if (resolveInfos != null && resolveInfos.size() > 0) {
            mTempAppsForUrl.clear();
            for (ResolveInfo resolveInfoToAdd : resolveInfos) {
                if (resolveInfoToAdd.activityInfo != null) {
                    boolean alreadyAdded = false;
                    for (int i = 0; i < mAppsForUrl.size(); i++) {
                        AppForUrl existing = mAppsForUrl.get(i);
                        if (existing.mResolveInfo.activityInfo.packageName.equals(resolveInfoToAdd.activityInfo.packageName)
                                && existing.mResolveInfo.activityInfo.name.equals(resolveInfoToAdd.activityInfo.name)) {
                            alreadyAdded = true;
                            if (existing.mUrl.equals(url) == false) {
                                if (url.getHost().contains(existing.mUrl.getHost())
                                        && url.getHost().length() > existing.mUrl.getHost().length()) {
                                    // don't update the url in this case. This means prevents, as an example, saving a host like
                                    // "mobile.twitter.com" instead of using "twitter.com". This occurs when loading
                                    // "https://twitter.com/lokibartleby/status/412160702707539968" with Tweet Lanes
                                    // and the official Twitter client installed.
                                } else {
                                    try {
                                        existing.mUrl = new URL(url.toString());   // Update the Url
                                    } catch (MalformedURLException e) {
                                        throw new RuntimeException("Malformed URL: " + url);
                                    }
                                }
                            }
                            break;
                        }
                    }

                    if (alreadyAdded == false) {
                        mTempAppsForUrl.add(resolveInfoToAdd);
                    }
                }
            }

            if (mTempAppsForUrl.size() > 0) {
                URL currentUrl;
                try {
                    currentUrl = new URL(url.toString());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    return;
                }

                // We need to handle the following case:
                //   * Load reddit.com/r/Android. The app to handle that URL might be "Reddit is Fun" or something similar.
                //   * Click on a link to play.google.com/store/, which is handled by the "Google Play" app.
                //   * The code below adds "Google Play" to the app list that contains "Reddit is Fun",
                //       even though "Reddit is Fun" is not applicable for this link.
                // Unfortunately there is no way reliable way to find out when a user has clicked on a link using the WebView.
                // http://stackoverflow.com/a/17937536/328679 is close, but doesn't work because it relies on onPageFinished()
                // being called, which will not be called if the current page is still loading when the link was clicked.
                //
                // So, in the event contains results, and these results reference a different URL that which matched the
                // resolveInfos passed in, clear mAppsForUrl.
                if (mAppsForUrl.size() > 0) {
                    URL firstUrl = mAppsForUrl.get(0).mUrl;
                    if ((currentUrl.getHost().contains(firstUrl.getHost())
                            && currentUrl.getHost().length() > firstUrl.getHost().length()) == false) {
                        mAppsForUrl.clear();    // start again
                    }
                }

                for (ResolveInfo resolveInfoToAdd : mTempAppsForUrl) {
                    mAppsForUrl.add(new AppForUrl(resolveInfoToAdd, currentUrl));
                }
            }

        } else {
            mAppsForUrl.clear();
        }
    }

    public void onAnimateOnScreen() {
        hidePopups();
        resetButtonPressedStates();
    }

    public void onAnimateOffscreen() {
        hidePopups();
        resetButtonPressedStates();
    }

    void onCurrentContentViewChanged(boolean isCurrent) {
        hidePopups();
        resetButtonPressedStates();
    }

    void onOrientationChanged() {
        invalidate();
    }

    private void updateUrl(String urlAsString) {
        if (urlAsString.equals(mUrl.toString()) == false) {
            try {
                Log.d(TAG, "change url from " + mUrl + " to " + urlAsString);
                mUrl = new URL(urlAsString);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Malformed URL: " + urlAsString);
            }
        }
    }

    URL getUrl() {
        return mUrl;
    }

    private void hidePopups() {
        if (mOverflowPopupMenu != null) {
            mOverflowPopupMenu.dismiss();
            mOverflowPopupMenu = null;
        }
        if (mLongPressAlertDialog != null) {
            mLongPressAlertDialog.dismiss();
            mLongPressAlertDialog = null;
        }
    }

    private void resetButtonPressedStates() {
        mShareButton.setIsTouched(false);
        mOpenEmbedButton.setIsTouched(false);
        mOpenInAppButton.setIsTouched(false);
        mOverflowButton.setIsTouched(false);
    }

    private boolean openInBrowser(String urlAsString) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(urlAsString));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (MainApplication.loadInBrowser(mContext, intent, true)) {
            mEventHandler.onDestroyBubble();
            return true;
        }

        return false;
    }

    // For security reasons, all callbacks should be in a self contained class
    public class JSEmbedHandler {

        private Runnable mUpdateOpenInAppRunnable = null;

        @JavascriptInterface
        public void onEmbeds(String string) {
            Log.d(TAG, "onEmbeds() - " + string);

            if (string == null || string.length() == 0) {
                return;
            }

            if (mYouTubeEmbedHelper == null) {
                mYouTubeEmbedHelper = new YouTubeEmbedHelper(mContext);
            }

            String[] strings = string.split(",");
            if (mYouTubeEmbedHelper.onEmbeds(strings)) {
                if (mUpdateOpenInAppRunnable == null) {
                    mUpdateOpenInAppRunnable = new Runnable() {
                        @Override
                        public void run() {
                            configureOpenEmbedButton();
                        }
                    };
                }

                mOpenEmbedButton.post(mUpdateOpenInAppRunnable);
            }
        }
    };

    private JSEmbedHandler mJSEmbedHandler;


}
