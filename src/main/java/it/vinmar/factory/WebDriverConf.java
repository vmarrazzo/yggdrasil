package it.vinmar.factory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * This object aggregates each configuration that can be
 * passed to {@code WebDriverFactory}
 */
public final class WebDriverConf {

	/**
	 * This {@code enum} defines supported browser type
	 */
	public enum WebBrowser {
		CHROME, CHROME_HEADLESS, FIREFOX, FIREFOX_HEADLESS, PHANTOMJS
	}

	private final WebBrowser webBrowser;
	private final Optional<URL> grid;
	private final Optional<String> proxy;
	private final Optional<String> noProxy;

	/**
	 * WebBrowser enum instance
	 *
	 * @return current enum value
	 */
	public WebBrowser getWebBrowser() {
		return webBrowser;
	}

	/**
	 * Url Selenium Grid
	 *
	 * @return {@code Optional} of {@code URL}
	 */
	public Optional<URL> getGrid() {
		return grid;
	}

	/**
	 * Proxy used in configuration
	 *
	 * @return {@code Optional} of {@code String}
	 */
	public Optional<String> getProxy() {
		return proxy;
	}

	/**
	 * "No Proxy" hosts list used in configuration
	 *
	 * @return {@code Optional} of {@code String}
	 */
	public Optional<String> getNoProxy() {
		return noProxy;
	}

	public WebDriverConf(
			final String webBrowserString,
			final String gridUrl,
			final String proxyString,
			final String noProxyString) {

		webBrowser = WebBrowser.valueOf(webBrowserString);

		URL url = null;
		try {
			url = new URL(gridUrl);
		} catch (final MalformedURLException e) {
			url = null;
		} finally {
			grid = Optional.ofNullable(url);
		}

		proxy = Optional.ofNullable(proxyString);
		noProxy = Optional.ofNullable(noProxyString);
	}

	public WebDriverConf(
			final String webBrowserString,
			final String gridUrl,
			final String proxyString) {
		this(webBrowserString, gridUrl, proxyString, null);
	}

	public WebDriverConf(
			final String webBrowserString,
			final String gridUrl) {
		this(webBrowserString, gridUrl, null, null);
	}

	public WebDriverConf(final String webBrowserString) {
		this(webBrowserString, null, null, null);
	}
}
