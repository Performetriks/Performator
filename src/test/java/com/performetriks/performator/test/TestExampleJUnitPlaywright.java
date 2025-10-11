package com.performetriks.performator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.base.HSRConfig;
import com.xresch.hsr.reporting.HSRReporterCSV;
import com.xresch.hsr.reporting.HSRReporterSysoutCSV;
import com.xresch.hsr.stats.HSRRecord;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class TestExampleJUnitPlaywright {
  // Shared between all tests in this class.
  static Playwright playwright;
  static Browser browser;

  // New instance for each test method.
  BrowserContext context;
  Page page;

  /************************************************************************
   * 
   ************************************************************************/
  @BeforeAll
  static void launchBrowser() {
	  
	//--------------------------
	// HSR Config
	HSRConfig.enable(5);
	HSRConfig.addReporter(new HSRReporterSysoutCSV(" | "));
	
	//--------------------------
	// Create Playwright
    playwright = Playwright.create();
    LaunchOptions launchOptions = new BrowserType.LaunchOptions();
    launchOptions.setHeadless(false);
    launchOptions.setSlowMo(1000);

    //HashMap<String, String> envVariables = new HashMap<>();
    //envVariables.put("PWDEBUG", "1");
    //launchOptions.setEnv(envVariables);
    
    browser = playwright.chromium().launch(launchOptions);
  }
  
  /************************************************************************
   * 
   ************************************************************************/
  @AfterAll
  static void closeBrowser() {
    playwright.close();
  }

  /************************************************************************
   * 
   ************************************************************************/
  @BeforeEach
  void createContextAndPage() {
    context = browser.newContext();
    page = context.newPage();
  }

  /************************************************************************
   * 
   ************************************************************************/
  @AfterEach
  void closeContext() {
    context.close();
  }

  /************************************************************************
   * 
   ************************************************************************/
  @Test
  void shouldClickButton() {
    page.navigate("data:text/html,<script>var result;</script><button onclick='result=\"Clicked\"'>Go</button>");
    page.locator("button").click();
    HSR.assertEquals("Clicked", page.evaluate("result"), "Button is clicked");
    HSR.assertEquals("Not Clicked", page.evaluate("result"), "Test for failing Assert");

  }

  /************************************************************************
   * 
   ************************************************************************/
  @Test
  void shouldCheckTheBox() {
    page.setContent("<input id='checkbox' type='checkbox'></input>");
   
    page.locator("input").check();
    
    HSR.assertTrue((Boolean) page.evaluate("() => window['checkbox'].checked"), "Checkbox is checked");

  }

  /*****************************************************************
   * 
   *****************************************************************/
  @Test
  void shouldSearchWiki() {
	
	//-------------------------------
	// 
	HSR.start("000_Open_Homepage");
		page.navigate("https://www.wikipedia.org/");
	HSR.end();
	
	//-------------------------------
	// 

	HSR.start("010_Enter_Playwright");
	    page.locator("input[name=\"search\"]").click();
	    page.locator("input[name=\"search\"]").fill("playwright");
	HSR.end();
	
	HSR.startGroup("MyGroup");
		HSR.startGroup("MySubGroup");
			//-------------------------------
			// 
			HSR.start("020_Execute_Search");
			    page.locator("input[name=\"search\"]").press("Enter");
			HSR.end();
			
			//-------------------------------
			// 
			HSR.assertEquals("https://en.wikipedia.org/wiki/Playwright"
					, page.url()
					,  "030_Assert_IsPlaywrightPage");
		HSR.end();
	HSR.end();

  }
}
