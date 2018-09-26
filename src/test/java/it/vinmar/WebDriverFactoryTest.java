package it.vinmar;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.Test;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;

import it.vinmar.WebDriverFactory.WebDriverConf;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

public class WebDriverFactoryTest {

	@Test
	public void testCreationViaParameters() {
		
		WebDriverFactory underTest = new WebDriverFactory("PHANTOMJS", Optional.empty(), Optional.empty(), Optional.empty());
		
		WebDriver driver = underTest.newResource();
		
		assertThat(driver, instanceOf(PhantomJSDriver.class));
		
		underTest.closeResource(driver);
	}
	
	@Test
	public void testCreationViaConfigurator() {

		WebDriverConf conf = new WebDriverConf("FIREFOX_HEADLESS");
		
		WebDriverFactory underTest = new WebDriverFactory(conf);
		
		WebDriver driver = underTest.newResource();
		
		assertThat(driver, instanceOf(FirefoxDriver.class));
		
		underTest.closeResource(driver);
	}
	
	@Test
	public void testFirefoxHeadlessWithCompleteProxy() {

		// used proxynova for unit test can fail!
		WebDriverConf conf = new WebDriverConf("FIREFOX_HEADLESS", null, "213.168.210.76:80", "localhost, 127.0.0.1");
		
		WebDriverFactory underTest = new WebDriverFactory(conf);
		
		WebDriver driver = underTest.newResource();
		
		assertThat(driver, instanceOf(FirefoxDriver.class));
		
		underTest.closeResource(driver);
	}	
	
	@Test
	public void testCreationWithSetupAction() {
			
			WebDriverConf conf = new WebDriverConf("CHROME_HEADLESS");
	
			Consumer<WebDriver> setupAction = 
					driver -> {
						try {
							driver.get("https://download.mozilla.org/?product=firefox-latest&lang=en-US");
						} catch (Throwable e) {
							// TODO: handle exception
						}
					};
			
			WebDriverFactory underTest = new WebDriverFactory(conf, setupAction);
			
			WebDriver driver = underTest.newResource();
			
			assertThat(driver, instanceOf(ChromeDriver.class));
			
			underTest.closeResource(driver);	
	}
	
	@Test
	public void testCreateFirefoxWithDebug() {

		System.setProperty("factory.debug", "true");
		WebDriverConf conf = new WebDriverConf("FIREFOX");
		
		WebDriverFactory underTest = new WebDriverFactory(conf);
		
		WebDriver driver = underTest.newResource();
		driver.manage().window().setPosition(new Point(-2000, 0));		
		
		assertThat(driver, instanceOf(FirefoxDriver.class));
		
		underTest.closeResource(driver);
		
		System.clearProperty("factory.debug");
	}
	
	@Test
	public void testCreateChromeWithDebug() {

		System.setProperty("factory.debug", "true");
		WebDriverConf conf = new WebDriverConf("CHROME");
		
		WebDriverFactory underTest = new WebDriverFactory(conf);
		
		WebDriver driver = underTest.newResource();
		driver.manage().window().setPosition(new Point(-2000, 0));
		
		assertThat(driver, instanceOf(ChromeDriver.class));
		
		underTest.closeResource(driver);
		
		System.clearProperty("factory.debug");
	}	
}
