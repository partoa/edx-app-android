package org.edx.mobile.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.inject.Inject;
import com.joanzapata.iconify.Icon;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.fonts.FontAwesomeIcons;

import org.edx.mobile.R;
import org.edx.mobile.base.BaseFragment;
import org.edx.mobile.event.NetworkConnectivityChangeEvent;
import org.edx.mobile.event.SessionIdRefreshEvent;
import org.edx.mobile.http.HttpStatus;
import org.edx.mobile.logger.Logger;
import org.edx.mobile.module.prefs.LoginPrefs;
import org.edx.mobile.services.EdxCookieManager;
import org.edx.mobile.util.NetworkUtil;
import org.edx.mobile.view.custom.URLInterceptorWebViewClient;

import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;
import roboguice.inject.InjectView;

import static org.edx.mobile.util.AppConstants.EMPTY_HTML;

/**
 * Provides a webview which authenticates the user before loading a page,
 * Javascript can also be passed in arguments for evaluation.
 */
public class AuthenticatedWebViewFragment extends BaseFragment {
    protected final Logger logger = new Logger(getClass().getName());
    public static final String ARG_URL = "ARG_URL";
    public static final String ARG_JAVASCRIPT = "ARG_JAVASCRIPT";

    @InjectView(R.id.loading_indicator)
    private ProgressBar progressWheel;

    @InjectView(R.id.course_unit_webView)
    protected WebView webView;

    @InjectView(R.id.content_unavailable_error_text)
    private TextView errorTextView;

    @Inject
    private LoginPrefs loginPrefs;

    private String url;
    private String javascript;
    private boolean pageIsLoaded;
    private boolean didReceiveError;

    public static Fragment newInstance(@NonNull String url) {
        return newInstance(url, null);
    }

    public static Fragment newInstance(@NonNull String url, @Nullable String javascript) {
        final Fragment fragment = new AuthenticatedWebViewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_JAVASCRIPT, javascript);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            url = getArguments().getString(ARG_URL);
            javascript = getArguments().getString(ARG_JAVASCRIPT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_course_unit_webview, container, false);
    }


    @SuppressLint({"AddJavascriptInterface", "SetJavaScriptEnabled"})
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        webView.clearCache(true);
        webView.getSettings().setJavaScriptEnabled(true);
        if (!TextUtils.isEmpty(javascript)) {
            webView.addJavascriptInterface(new EdxJsInterface(), "JsInterface");
        }
        URLInterceptorWebViewClient client =
                new URLInterceptorWebViewClient(getActivity(), webView) {

                    @Override
                    public void onReceivedError(WebView view, int errorCode,
                                                String description, String failingUrl) {
                        // If error occurred for web page request
                        if (failingUrl != null && failingUrl.equals(view.getUrl())) {
                            didReceiveError = true;
                            hideLoadingProgress();
                            pageIsLoaded = false;
                            showErrorMessage(R.string.network_error_message,
                                    FontAwesomeIcons.fa_exclamation_circle);
                        }
                    }

                    @Override
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                    WebResourceResponse errorResponse) {
                        // If error occurred for web page request
                        if (request.getUrl().toString().equals(view.getUrl())) {
                            didReceiveError = true;
                            switch (errorResponse.getStatusCode()) {
                                case HttpStatus.FORBIDDEN:
                                case HttpStatus.UNAUTHORIZED:
                                case HttpStatus.NOT_FOUND:
                                    EdxCookieManager.getSharedInstance(getContext())
                                            .tryToRefreshSessionCookie();
                                    break;
                            }
                            showErrorMessage(R.string.network_error_message,
                                    FontAwesomeIcons.fa_exclamation_circle);
                        }
                    }

                    public void onPageFinished(WebView view, String url) {
                        if (!NetworkUtil.isConnected(getContext())) {
                            showErrorView(getString(R.string.reset_no_network_message), FontAwesomeIcons.fa_wifi);
                            hideLoadingProgress();
                            pageIsLoaded = false;
                            return;
                        }
                        if (didReceiveError) {
                            didReceiveError = false;
                            return;
                        }
                        if (url != null && url.equals("data:text/html," + EMPTY_HTML)) {
                            //we load a local empty html page to release the memory
                        } else {
                            pageIsLoaded = true;
                            hideErrorMessage();
                        }
                        if (pageIsLoaded && !TextUtils.isEmpty(javascript)) {
                            evaluateJavascript();
                        } else {
                            hideLoadingProgress();
                        }
                    }
                };
        client.setAllLinksAsExternal(true);

        tryToLoadWebView(true);
    }

    private void evaluateJavascript() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(javascript, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    hideLoadingProgress();
                }
            });
        } else {
            webView.loadUrl("javascript:" + javascript);
            // Javascript evaluation takes some time, so hide progressbar after 1 sec
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    hideLoadingProgress();
                }
            }, 1000);
        }
    }

    public class EdxJsInterface {
        @JavascriptInterface
        public void showErrorMessage(@NonNull final String errorMsg) {
            if (!TextUtils.isEmpty(errorMsg)) {
                webView.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                didReceiveError = true;
                                showErrorView(errorMsg, FontAwesomeIcons.fa_exclamation_circle);
                            }
                        }
                );
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(NetworkConnectivityChangeEvent event) {
        if (NetworkUtil.isConnected(getContext())) {
            hideErrorMessage();
        } else {
            showErrorMessage(R.string.reset_no_network_message, FontAwesomeIcons.fa_wifi);
        }
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(SessionIdRefreshEvent event) {
        if (event.success) {
            tryToLoadWebView(false);
        } else {
            hideLoadingProgress();
        }
    }

    /**
     * Shows the error message with the given icon, if the web page failed to load
     *
     * @param errorMsg  The error message to show
     * @param errorIcon The error icon to show with the error message
     */
    private void showErrorMessage(@StringRes int errorMsg, @NonNull Icon errorIcon) {
        if (!pageIsLoaded) {
            tryToClearWebView();
            showErrorView(getString(errorMsg), errorIcon);
        }
    }

    private void showErrorView(@NonNull String errorMsg, @NonNull Icon errorIcon) {
        final Context context = getContext();
        errorTextView.setVisibility(View.VISIBLE);
        errorTextView.setText(errorMsg);
        errorTextView.setCompoundDrawablesWithIntrinsicBounds(null,
                new IconDrawable(context, errorIcon)
                        .sizeRes(context, R.dimen.content_unavailable_error_icon_size)
                        .colorRes(context, R.color.edx_brand_gray_back),
                null, null
        );
    }

    /**
     * Hides the error message view and reloads the web page if it wasn't already loaded
     */
    private void hideErrorMessage() {
        errorTextView.setVisibility(View.GONE);
        if (!pageIsLoaded || didReceiveError) {
            tryToLoadWebView(true);
        }
    }

    private void tryToLoadWebView(boolean forceLoad) {
        System.gc(); //there is a well known Webview Memory Issue With Galaxy S3 With 4.3 Update

        if ((!forceLoad && pageIsLoaded) || progressWheel == null) {
            return;
        }

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        if (!NetworkUtil.isConnected(getContext())) {
            showErrorMessage(R.string.reset_no_network_message, FontAwesomeIcons.fa_wifi);
            return;
        }

        showLoadingProgress();

        if (!TextUtils.isEmpty(url)) {
            Map<String, String> map = new HashMap<>();
            final String token = loginPrefs.getAuthorizationHeader();
            if (token != null) {
                map.put("Authorization", token);
            }

            // Requery the session cookie if unavailable or expired.
            final EdxCookieManager cookieManager = EdxCookieManager.getSharedInstance(getContext());
            if (cookieManager.isSessionCookieMissingOrExpired()) {
                cookieManager.tryToRefreshSessionCookie();
            } else {
                didReceiveError = false;
                webView.loadUrl(url);
            }
        }
    }

    private void tryToClearWebView() {
        pageIsLoaded = false;
        if (webView != null) {
            webView.loadData(EMPTY_HTML, "text/html", "UTF-8");
        }
    }

    private void showLoadingProgress() {
        if (!TextUtils.isEmpty(javascript)) {
            // Hide webview to disable a11y during loading page, disabling a11y is not working in this case
            webView.setVisibility(View.GONE);
        }
        progressWheel.setVisibility(View.VISIBLE);
    }

    private void hideLoadingProgress() {
        progressWheel.setVisibility(View.GONE);
        if (didReceiveError) {
            webView.setVisibility(View.GONE);
        } else {
            webView.setVisibility(View.VISIBLE);
        }
    }
}
