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
public class WebDriverFactory implements it.vinmar.ResourceArbiter.Factory<WebDriver> {

	/**
	 * 
	 */
	private final static Logger logger__ = LoggerFactory.getLogger("root");

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

		public WebDriverConf(String webBrowserString, String gridUrl, String proxyString, String noProxyString) {

			webBrowser = WebBrowsers.valueOf(webBrowserString);

			URL url = null;
			try {
				url = new URL(gridUrl);
			} catch (MalformedURLException e) {
				url = null;
			} finally {
				grid = Optional.ofNullable(url);
			}

			proxy = Optional.ofNullable(proxyString);
			noProxy = Optional.ofNullable(noProxyString);
		}

		public WebDriverConf(String webBrowserString, String gridUrl, String proxyString) {
			this(webBrowserString, gridUrl, proxyString, null);
		}

		public WebDriverConf(String webBrowserString, String gridUrl) {
			this(webBrowserString, gridUrl, null, null);
		}

		public WebDriverConf(String webBrowserString) {
			this(webBrowserString, null, null, null);
		}
	}

	/**
	 * Factory creates new  {@code WebDriver} based on this enum
	 */
	private WebBrowsers browserType;
	
	/**
	 * {@code Optional} value of proxy in &lt;host&gt;:&lt;port&gt; format
	 */
	private Optional<String> proxy;
	
	/**
	 * {@code Optional} value of Selenium Grid {@code URL}
	 */	
	private Optional<URL> grid;

	/**
	 * {@code Optional} comma separated list of hosts that bypass proxy
	 */
	private Optional<String> noProxy;

	/**
	 * Proxy object for Selenium
	 */
	private Proxy proxyObject = null;

	/**
	 * WebDriver counter
	 */
	private AtomicInteger counter;

	/**
	 * Factory constructor with declared sub-configuration.
	 * 
	 * @param inBrowserType
	 *            string describes configured browser
	 * @param proxy
	 *            if behind proxy use format &lt;host&gt;:&lt;port&gt;
	 * @param grid
	 *            if used Selenium Grid here will be placed its URL
	 * @param noProxy
	 *            comma separated list of "no proxy" hosts
	 */
	public WebDriverFactory(String inBrowserType, Optional<String> proxy, Optional<URL> grid,
			Optional<String> noProxy) {

		logger__.info(String.format("#### Creation of WebDriverFactory for %s", inBrowserType));
		proxy.ifPresent(urlproxy -> logger__.info(String.format("#### Using proxy -> %s", urlproxy.toString())));
		noProxy.ifPresent(listNoProxy -> logger__
				.info(String.format("#### Using proxy -> %s", listNoProxy.split(",").toString())));
		grid.ifPresent(urlgrid -> logger__.info(String.format("#### Using Selenium Grid -> %s", urlgrid.toString())));

		browserType = WebBrowsers.valueOf(inBrowserType);
		this.proxy = proxy;
		this.grid = grid;
		this.noProxy = noProxy;

		proxyObject = new Proxy();

		if (proxy.isPresent()) {
			proxyObject.setProxyType(ProxyType.MANUAL);
			proxyObject.setHttpProxy(proxy.get());
			proxyObject.setSslProxy(proxy.get());
		}

		counter = new AtomicInteger(0);
	}

	/**
	 * This constructor use WebDriverConf object as configuration
	 * 
	 * @param conf is {@code WebDriverConf} object with necessary configuration
	 */
	public WebDriverFactory(WebDriverConf conf) {
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
	 * @param setupAction is setup action used after driver creation
	 */
	public WebDriverFactory(WebDriverConf conf, Consumer<WebDriver> setupAction) {

		this(conf);
		this.setupAction = Optional.ofNullable(setupAction);
	}

	/**
	 * Private method to generate FirefoxDriverOptions file.
	 * 
	 * @param headless
	 * @return
	 */
	private FirefoxOptions createFirefoxOptions(Boolean headless) {

		FirefoxOptions fops = new FirefoxOptions();
		fops.setHeadless(headless);
		
		proxy.ifPresent(ok -> fops.setCapability(CapabilityType.PROXY, proxyObject));

		System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE,"true");
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE,"/dev/null");
		
		FirefoxProfile fp = new FirefoxProfile();
		fp.setPreference("geo.prompt.testing", Boolean.TRUE);
		fp.setPreference("geo.prompt.testing.allow", Boolean.TRUE);
		fp.setPreference("geo.wifi.uri",
				"data:application/json,{\"status\":\"OK\",\"accuracy\":10.0,\"location\":{\"lat\":45.465454,\"lng\":9.1865159}}");

		if (!headless && Boolean.parseBoolean(System.getProperty("factory.debug", "false"))) {

			Path tempChroPath = null;
			try {
				InputStream chroPathResource = getClass().getResourceAsStream("/ChroPath.xpi");
				
				tempChroPath = Files.createTempFile("ChroPath", ".xpi");
				Files.copy(chroPathResource, tempChroPath, StandardCopyOption.REPLACE_EXISTING);
				
				fp.addExtension(tempChroPath.toFile());

			} catch (IOException e) {
				logger__.error("XXXX Error during debug procedure -> no ChroPath available.");
				throw new IllegalStateException("XXXX Error during debug procedure -> no ChroPath available.");				
			} finally {
				if (tempChroPath != null)
					tempChroPath.toFile().deleteOnExit();
			}
		}		
		
		noProxy.ifPresent(noProxyString -> fp.setPreference("network.proxy.no_proxies_on", noProxyString));

		fops.setProfile(fp);

		return fops;
	}

	/**
	 * This method creates a local FirefoxDriver instance
	 * 
	 * @param headless, true if headless
	 * 
	 * @return
	 */
	private FirefoxDriver createLocalFirefoxDriver(Boolean headless) {

		// handle download manager behind proxy
		if (proxy.isPresent())
			WebDriverManager.getInstance(DriverManagerType.FIREFOX).proxy(proxy.get()).setup();
		else
			WebDriverManager.getInstance(DriverManagerType.FIREFOX).setup();

		return new FirefoxDriver(createFirefoxOptions(headless));
	}

	/**
	 * 
	 * @param headless
	 * @return
	 */
	private ChromeOptions createChromeOptions(Boolean headless) {

		ChromeOptions cops = new ChromeOptions();

		if (headless)
			cops.addArguments("--headless");

		if (!headless && Boolean.parseBoolean(System.getProperty("factory.debug", "false"))) {

			Path tempChroPath = null;
			try {
				InputStream chroPathResource = getClass().getResourceAsStream("/ChroPath.crx");
				
				tempChroPath = Files.createTempFile("ChroPath", ".crx");
				Files.copy(chroPathResource, tempChroPath, StandardCopyOption.REPLACE_EXISTING);
				
				cops.addExtensions(tempChroPath.toFile());		

			} catch (IOException e) {
				logger__.error("XXXX Error during debug procedure -> no ChroPath available.");
				throw new IllegalStateException("XXXX Error during debug procedure -> no ChroPath available.");
			} finally {
				if (tempChroPath != null)
					tempChroPath.toFile().deleteOnExit();
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
	 * @return
	 */
	private ChromeDriver createLocalChromeDriver(Boolean headless) {

		if (proxy.isPresent())
			WebDriverManager.getInstance(DriverManagerType.CHROME).proxy(proxy.get()).setup();
		else
			WebDriverManager.getInstance(DriverManagerType.CHROME).setup();

		return new ChromeDriver(createChromeOptions(headless));
	}

	@Override
	public WebDriver newResource() {

		WebDriver resp;

		Integer index = counter.incrementAndGet();

		logger__.info(String.format("#### Creation of driver instance nr. %s", index));

		switch (browserType) {
		case FIREFOX:
			if (grid.isPresent())
				resp = new RemoteWebDriver(grid.get(), createFirefoxOptions(false));
			else
				resp = createLocalFirefoxDriver(false);
			break;

		case FIREFOX_HEADLESS:
			if (grid.isPresent())
				resp = new RemoteWebDriver(grid.get(), DesiredCapabilities.firefox());
			else
				resp = createLocalFirefoxDriver(true);
			break;

		case CHROME:
			if (grid.isPresent())
				resp = new RemoteWebDriver(grid.get(), createChromeOptions(false));
			else
				resp = createLocalChromeDriver(false);
			break;

		case CHROME_HEADLESS:
			if (grid.isPresent())
				resp = new RemoteWebDriver(grid.get(), DesiredCapabilities.chrome());
			else
				resp = createLocalChromeDriver(true);
			break;

		case PHANTOMJS:
			DesiredCapabilities caps = new DesiredCapabilities();
			caps.setJavascriptEnabled(true);

			if (proxy.isPresent()) {
				WebDriverManager.getInstance(DriverManagerType.PHANTOMJS).proxy(proxy.get()).setup();

				caps.setCapability(PhantomJSDriverService.PHANTOMJS_CLI_ARGS,
						new String[] { "--proxy=" + proxy.get(), "--web-security=false", "--ssl-protocol=any",
								"--ignore-ssl-errors=true", "--webdriver-loglevel=INFO",
								String.format("--webdriver-logfile=./phantomjsdriver_%03d.log", index) });
			} else
				WebDriverManager.getInstance(DriverManagerType.PHANTOMJS).setup();
			resp = new PhantomJSDriver(caps);
			break;

		default:
			throw new IllegalStateException("Missing configuration for WebDriver creation!");
		}

		setupAction.ifPresent(setupAction -> setupAction.accept(resp));

		return resp;
	}

	@Override
	public void closeResource(WebDriver driver) {
		driver.quit();
	}
}
