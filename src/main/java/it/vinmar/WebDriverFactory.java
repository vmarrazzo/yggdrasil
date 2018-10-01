package it.vinmar;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.openqa.selenium.Proxy;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bonigarcia.wdm.DriverManagerType;
import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * This is a factory object to create multiple WebDriver instance based on configuration.
 * It implementation is based on {@code Factory} interface.
 */
public final class WebDriverFactory implements it.vinmar.ResourceArbiter.Factory<WebDriver> {

	/**
	 * Logger
	 */
	private final Logger logger = LoggerFactory.getLogger("root");

	/**
	 * This {@code enum} defines supported browser type
	 */
	public enum WebBrowsers {
		CHROME, CHROME_HEADLESS, FIREFOX, FIREFOX_HEADLESS, PHANTOMJS
	}

	/**
	 * This object aggregates each configuration that can be passed to {@code WebDriverFactory}
	 */
	public static class WebDriverConf {
		final WebBrowsers webBrowser;
		final Optional<URL> grid;
		final Optional<String> proxy;
		final Optional<String> noProxy;

		public WebDriverConf(final String webBrowserString, final String gridUrl, final String proxyString, final String noProxyString) {

			webBrowser = WebBrowsers.valueOf(webBrowserString);

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

		public WebDriverConf(final String webBrowserString, final String gridUrl, final String proxyString) {
			this(webBrowserString, gridUrl, proxyString, null);
		}

		public WebDriverConf(final String webBrowserString, final String gridUrl) {
			this(webBrowserString, gridUrl, null, null);
		}

		public WebDriverConf(final String webBrowserString) {
			this(webBrowserString, null, null, null);
		}
	}

	/**
	 * Factory creates new  {@code WebDriver} based on this enum
	 */
	private final WebBrowsers browserType;

	/**
	 * {@code Optional} value of proxy in &lt;host&gt;:&lt;port&gt; format
	 */
	private final Optional<String> proxy;

	/**
	 * {@code Optional} value of Selenium Grid {@code URL}
	 */
	private final Optional<URL> grid;

	/**
	 * {@code Optional} comma separated list of hosts that bypass proxy
	 */
	private final Optional<String> noProxy;

	/**
	 * Proxy object for Selenium
	 */
	private Proxy proxyObject = null;

	/**
	 * WebDriver counter
	 */
	private final AtomicInteger counter;

	/**
	 * Factory constructor with declared sub-configuration.
	 *
	 * @param inBrowserType
	 *            string describes configured browser
	 * @param inProxy
	 *            if behind proxy use format &lt;host&gt;:&lt;port&gt;
	 * @param inGrid
	 *            if used Selenium Grid here will be placed its URL
	 * @param inNoProxy
	 *            comma separated list of "no proxy" hosts
	 */
	public WebDriverFactory(final String inBrowserType,
			final Optional<String> inProxy,
			final Optional<URL> inGrid,
			final Optional<String> inNoProxy) {

		logger.info(String.format("#### Creation of WebDriverFactory for %s", inBrowserType));
		inProxy.ifPresent(urlproxy -> logger
				.info(String.format("#### Using proxy -> %s", urlproxy.toString())));
		inNoProxy.ifPresent(listNoProxy -> logger
				.info(String.format("#### Using proxy -> %s", listNoProxy.split(",").toString())));
		inGrid.ifPresent(urlgrid -> logger
				.info(String.format("#### Using Selenium Grid -> %s", urlgrid.toString())));

		browserType = WebBrowsers.valueOf(inBrowserType);
		this.proxy = inProxy;
		this.grid = inGrid;
		this.noProxy = inNoProxy;

		proxyObject = new Proxy();

		if (inProxy.isPresent()) {
			proxyObject.setProxyType(ProxyType.MANUAL);
			proxyObject.setHttpProxy(inProxy.get());
			proxyObject.setSslProxy(inProxy.get());
		}

		counter = new AtomicInteger(0);
	}

	/**
	 * This constructor use WebDriverConf object as configuration
	 *
	 * @param conf is {@code WebDriverConf} object with necessary configuration
	 */
	public WebDriverFactory(final WebDriverConf conf) {
		this(conf.webBrowser.toString(), conf.proxy, conf.grid, conf.noProxy);
	}

	/**
	 * Setup action used during creation of new WebDriver instance
	 */
	private Optional<Consumer<WebDriver>> setupAction = Optional.empty();

	/**
	 * This constructor accepts as input {@code WebDriverConf} object and {@code Consumer} of {@code WebDriver}. The last object is used as setup action when a new {@code WebDriver} is created.
	 *
	 * @param conf is WebDriverConf object
	 * @param inSetupAction is setup action used after driver creation
	 */
	public WebDriverFactory(final WebDriverConf conf, final Consumer<WebDriver> inSetupAction) {

		this(conf);
		this.setupAction = Optional.ofNullable(inSetupAction);
	}

	/**
	 * Private method to generate FirefoxOptions object.
	 *
	 * @param headless
	 *
	 * @return {@code FirefoxOptions} according current configuration
	 */
	private FirefoxOptions createFirefoxOptions(final Boolean headless) {

		final FirefoxOptions fops = new FirefoxOptions();
		fops.setHeadless(headless);

		proxy.ifPresent(ok -> fops.setCapability(CapabilityType.PROXY, proxyObject));

		System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE, "true");
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null");

		final FirefoxProfile fp = new FirefoxProfile();
		fp.setPreference("geo.prompt.testing", Boolean.TRUE);
		fp.setPreference("geo.prompt.testing.allow", Boolean.TRUE);
		fp.setPreference("geo.wifi.uri",
				"data:application/json,{\"status\":\"OK\",\"accuracy\":10.0,\"location\":{\"lat\":37.619431,\"lng\":-112.166504}}");

		if (!headless && Boolean.parseBoolean(System.getProperty("factory.debug", "false"))) {

			Path tempChroPath = null;
			try {
				final InputStream chroPathResource = getClass().getResourceAsStream("/ChroPath.xpi");

				tempChroPath = Files.createTempFile("ChroPath", ".xpi");
				Files.copy(chroPathResource, tempChroPath, StandardCopyOption.REPLACE_EXISTING);

				fp.addExtension(tempChroPath.toFile());

			} catch (final IOException e) {
				String msg = "XXXX Error during debug procedure -> no ChroPath available.";
				logger.error(msg);
				throw new IllegalStateException(msg);
			} finally {
				if (tempChroPath != null) {
					tempChroPath.toFile().deleteOnExit();
				}
			}
		}

		noProxy.ifPresent(noProxyString -> fp.setPreference("network.proxy.no_proxies_on", noProxyString));

		fops.setProfile(fp);

		return fops;
	}

	/**
	 * This method creates a local FirefoxDriver instance
	 *
	 * @param headless
	 *
	 * @return {@code FirefoxDriver} instance
	 */
	private FirefoxDriver createLocalFirefoxDriver(final Boolean headless) {

		// handle download manager behind proxy
		if (proxy.isPresent()) {
			WebDriverManager.getInstance(DriverManagerType.FIREFOX).proxy(proxy.get()).setup();
		} else {
			WebDriverManager.getInstance(DriverManagerType.FIREFOX).setup();
		}

		return new FirefoxDriver(createFirefoxOptions(headless));
	}

	/**
	 * Private method to generate ChromeOptions object.
	 *
	 * @param headless
	 *
	 * @return {@code ChromeOptions} according current configuration
	 */
	private ChromeOptions createChromeOptions(final Boolean headless) {

		final ChromeOptions cops = new ChromeOptions();

		if (headless) {
			cops.addArguments("--headless");
		}

		if (!headless && Boolean.parseBoolean(System.getProperty("factory.debug", "false"))) {

			Path tempChroPath = null;
			try {
				final InputStream chroPathResource = getClass().getResourceAsStream("/ChroPath.crx");

				tempChroPath = Files.createTempFile("ChroPath", ".crx");
				Files.copy(chroPathResource, tempChroPath, StandardCopyOption.REPLACE_EXISTING);

				cops.addExtensions(tempChroPath.toFile());

			} catch (final IOException e) {
				String msg = "XXXX Error during debug procedure -> no ChroPath available.";
				logger.error(msg);
				throw new IllegalStateException(msg);
			} finally {
				if (tempChroPath != null) {
					tempChroPath.toFile().deleteOnExit();
				}
			}
		}

		proxy.ifPresent(ok -> cops.setCapability(CapabilityType.PROXY, proxyObject));

		return cops;
	}

	/**
	 * This method creates a local ChromeDriver instance
	 *
	 * @param headless, true if headless
	 *
	 * @return {@code ChromeDriver} instance
	 */
	private ChromeDriver createLocalChromeDriver(final Boolean headless) {

		if (proxy.isPresent()) {
			WebDriverManager.getInstance(DriverManagerType.CHROME).proxy(proxy.get()).setup();
		} else {
			WebDriverManager.getInstance(DriverManagerType.CHROME).setup();
		}

		return new ChromeDriver(createChromeOptions(headless));
	}

	@Override
	public WebDriver newResource() {

		WebDriver resp;

		final Integer index = counter.incrementAndGet();

		logger.info(String.format("#### Creation of driver instance nr. %s", index));

		switch (browserType) {
		case FIREFOX:
			if (grid.isPresent()) {
				resp = new RemoteWebDriver(grid.get(), createFirefoxOptions(false));
			} else {
				resp = createLocalFirefoxDriver(false);
			}
			break;

		case FIREFOX_HEADLESS:
			if (grid.isPresent()) {
				resp = new RemoteWebDriver(grid.get(), DesiredCapabilities.firefox());
			} else {
				resp = createLocalFirefoxDriver(true);
			}
			break;

		case CHROME:
			if (grid.isPresent()) {
				resp = new RemoteWebDriver(grid.get(), createChromeOptions(false));
			} else {
				resp = createLocalChromeDriver(false);
			}
			break;

		case CHROME_HEADLESS:
			if (grid.isPresent()) {
				resp = new RemoteWebDriver(grid.get(), DesiredCapabilities.chrome());
			} else {
				resp = createLocalChromeDriver(true);
			}
			break;

		case PHANTOMJS:
			final DesiredCapabilities caps = new DesiredCapabilities();
			caps.setJavascriptEnabled(true);

			if (proxy.isPresent()) {
				WebDriverManager.getInstance(DriverManagerType.PHANTOMJS).proxy(proxy.get()).setup();

				caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS,
						new String[] {
								"--proxy=" + proxy.get(), "--web-security=false", "--ssl-protocol=any",
								"--ignore-ssl-errors=true", "--webdriver-loglevel=INFO",
								String.format("--webdriver-logfile=./phantomjsdriver_%03d.log", index) });
			} else {
				WebDriverManager.getInstance(DriverManagerType.PHANTOMJS).setup();
			}
			resp = new PhantomJSDriver(caps);
			break;

		default:
			throw new IllegalStateException("Missing configuration for WebDriver creation!");
		}

		setupAction.ifPresent(action -> action.accept(resp));

		return resp;
	}

	@Override
	public void closeResource(final WebDriver driver) {
		driver.quit();
	}
}
