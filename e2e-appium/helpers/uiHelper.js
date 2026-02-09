/**
 * UIHelper - Common UI interaction helpers for E2E tests
 */

const selectors = require('./selectorHelpers');
const adb = require('./adb');

class UIHelper {
  /**
   * Step 1: App is open (Appium launched it). Brief wait for Splash to render, then onboarding is handled.
   * Expected: Splash visible, MainActivity, BatteryService may start. Check: logs.
   */
  static async waitForSplashAndBackground(scenarioId) {
    console.log(`[${scenarioId}] --- Step 1: App open (Splash + background start) ---`);
    console.log(`[${scenarioId}]   Expected: Splash, MainActivity, BatteryService start. Check: logs below.`);
    await browser.pause(2000);
  }

  /**
   * Step 2: Onboarding. Expected: Skip or first onboarding screen. Check: Skip visible and clicked or absent.
   */
  static async handleOnboarding(scenarioId) {
    console.log(`[${scenarioId}] --- Step 2: Onboarding (Skip) ---`);
    console.log(`[${scenarioId}]   Expected: Skip or onboarding. Check: Skip clicked or not found.`);
    const skip = await browser.$(selectors.byTextContains('Skip'));
    
    try {
      await skip.waitForDisplayed({ timeout: 15000 });
      if (await skip.isDisplayed()) {
        await skip.click();
        console.log(`[${scenarioId}]    ✓ Skip button clicked`);
        await browser.pause(500);
        return true;
      }
    } catch (e) {
      console.log(`[${scenarioId}]    ⊘ Skip button not found or not displayed`);
    }
    
    return false;
  }

  /**
   * Step 3: Terms. Expected: Terms checkbox/text visible. Check: checkbox checked or terms clicked.
   */
  static async acceptTerms(scenarioId) {
    console.log(`[${scenarioId}] --- Step 3: Terms & Conditions ---`);
    console.log(`[${scenarioId}]   Expected: Terms screen. Check: checkbox checked or terms clicked.`);
    
    const checkbox = await browser.$(selectors.byCheckable(false));
    const termsText = await browser.$(selectors.byTextContains('I agree to the Terms'));
    
    try {
      await termsText.waitForDisplayed({ timeout: 10000 });
    } catch (e) {
      console.log(`[${scenarioId}]    ⚠️  Terms screen not found`);
      return false;
    }
    
    // Try checkbox first
    if (await checkbox.isDisplayed().catch(() => false)) {
      await checkbox.click();
      console.log(`[${scenarioId}]    ✓ Terms checkbox checked`);
    } else if (await termsText.isDisplayed()) {
      await termsText.click();
      console.log(`[${scenarioId}]    ✓ Terms text clicked`);
    } else {
      console.log(`[${scenarioId}]    ⚠️  Could not interact with terms`);
      return false;
    }
    
    await browser.pause(600);
    return true;
  }

  /**
   * Step 4: Get Started. Expected: Button enabled after terms. Check: Get Started clicked.
   */
  static async clickGetStarted(scenarioId) {
    console.log(`[${scenarioId}] --- Step 4: Get Started ---`);
    console.log(`[${scenarioId}]   Expected: Get Started enabled. Check: clicked.`);
    
    const getStarted = await browser.$(selectors.byTextContains('Get Started'));
    
    try {
      await getStarted.waitForDisplayed({ timeout: 10000 });
      await getStarted.click();
      console.log(`[${scenarioId}]    ✓ Get Started clicked`);
      await browser.pause(1000);
      return true;
    } catch (e) {
      console.log(`[${scenarioId}]    ❌ Get Started button not found`);
      return false;
    }
  }

  /**
   * Step 5: Location permission. Click in-app Continue, then system Allow / While using.
   * If system dialog is never found, grant permissions via ADB so app can show loading → Home.
   */
  static async handleLocationPermission(scenarioId, deviceId) {
    console.log(`[${scenarioId}] --- Step 5: Location permission ---`);
    console.log(`[${scenarioId}]   Expected: Continue → system Allow / While using; else ADB grant.`);
    await browser.pause(3000);

    const systemSelectors = [
      { selector: selectors.byPermissionAllowButton(), name: 'Allow (id)' },
      { selector: selectors.byPermissionAllowButtonLegacy(), name: 'Allow (id legacy)' },
      { selector: selectors.byButtonTextContains('Allow'), name: 'Allow (Button)' },
      { selector: selectors.byButtonTextContains('While using'), name: 'While using (Button)' },
      { selector: selectors.byButtonTextContains('Only this time'), name: 'Only this time (Button)' },
      { selector: selectors.byButtonTextContains('Allow all the time'), name: 'Allow all the time (Button)' },
      { selector: selectors.byButtonTextContains('Precise'), name: 'Precise (Button)' },
      { selector: selectors.byButtonTextContains('Approximate'), name: 'Approximate (Button)' },
      { selector: selectors.byButtonTextContains('OK'), name: 'OK (Button)' },
      { selector: selectors.byClickableTextContains('Allow'), name: 'Allow (clickable)' },
      { selector: selectors.byClickableTextContains('While using'), name: 'While using (clickable)' }
    ];

    let systemDialogClicked = false;
    for (let round = 0; round < 8; round++) {
      await browser.pause(round === 0 ? 0 : 2000);
      if (round === 0) {
        const continueEl = await browser.$(selectors.byTextContains('Continue'));
        if (await continueEl.isDisplayed().catch(() => false)) {
          await continueEl.click();
          console.log(`[${scenarioId}]   ✓ Continue (in-app)`);
          await browser.pause(3000);
        }
      }
      for (const { selector, name } of systemSelectors) {
        const el = await browser.$(selector);
        if (await el.isDisplayed().catch(() => false)) {
          await el.click();
          console.log(`[${scenarioId}]   ✓ ${name}`);
          systemDialogClicked = true;
          await browser.pause(2000);
          break;
        }
      }
      if (systemDialogClicked) break;
    }

    if (!systemDialogClicked && deviceId) {
      console.log(`[${scenarioId}]   Granting permissions via ADB so app can proceed to Home.`);
      adb.grantAppPermissions(deviceId);
      adb.resumeApp(deviceId);
      await browser.pause(3500);
    }
    await browser.pause(2000);
    return true;
  }

  /**
   * Step 6: Home screen. Require at least 2 tab indicators so we don't false-pass on a single "Carbon" elsewhere.
   */
  static async waitForHomeScreen(scenarioId, maxRetries = 3) {
    console.log(`[${scenarioId}] --- Step 6: Home screen ---`);
    console.log(`[${scenarioId}]   Expected: Auth + getDeviceInfo, then Home. Check: at least 2 of Climate/Carbon/Battery/Station/Profile.`);
    const homeIndicators = ['Climate', 'Carbon', 'Battery', 'Station', 'Profile'];
    let retry = 0;

    while (retry <= maxRetries) {
      const visible = [];
      for (const text of homeIndicators) {
        const el = await browser.$(selectors.byTextContains(text));
        if (await el.isDisplayed().catch(() => false)) visible.push(text);
      }
      if (visible.length >= 2) {
        console.log(`[${scenarioId}]    ✓ Home screen confirmed (${visible.length} indicators: ${visible.join(', ')})`);
        return true;
      }

      if (retry < maxRetries) {
        console.log(`[${scenarioId}]    ⏳ Seen: [${visible.join(', ') || 'none'}]. Retry ${retry + 1}/${maxRetries} in 5s...`);
        await browser.pause(5000);
        retry++;
      } else {
        console.log(`[${scenarioId}]    ❌ Home not found (need 2+ of ${homeIndicators.join(', ')})`);
        return false;
      }
    }
    return false;
  }

  /**
   * Step 7: Notifications. Check notification shade for app notifications.
   */
  static async checkActiveNotifications(scenarioId, appPackage) {
    try {
      console.log(`[${scenarioId}] --- Step 7: Notification shade ---`);
      console.log(`[${scenarioId}]   Expected: Check notification shade for app notifications.`);
      await browser.execute('mobile: openNotifications');
      await browser.pause(1500);

      const keywords = [
        'Station Code',
        'station code',
        'Not green',
        'Climate',
        'Location',
        'Charging',
        'Battery',
        appPackage
      ];
      
      let found = [];

      for (const keyword of keywords) {
        const el = await browser.$(selectors.byTextContains(keyword));
        if (await el.isDisplayed().catch(() => false)) {
          try {
            const text = await el.getText();
            if (!found.some(n => n.text === text)) {
              found.push({ keyword, text });
              console.log(`[${scenarioId}]    🔔 Found: "${keyword}" → "${text}"`);
            }
          } catch (e) {
            // Ignore getText errors
          }
        }
      }

      if (found.length === 0) {
        console.log(`[${scenarioId}]    ✓ No app notifications in shade`);
      }

      // Close notification shade
      await browser.back();
      await browser.pause(500);

      return found;
    } catch (error) {
      console.log(`[${scenarioId}]    ⚠️  Error checking notifications: ${error.message}`);
      await browser.back().catch(() => {});
      return [];
    }
  }

  /**
   * Verifies Station Code notification presence/absence
   */
  static verifyStationCodeNotification(scenarioId, notifications, shouldExist) {
    const hasStationCode = notifications.some(n => 
      /station.*code/i.test(n.text) || /station.*code/i.test(n.keyword)
    );

    if (shouldExist && !hasStationCode) {
      console.log(`[${scenarioId}]    ❌ FAIL: Expected Station Code notification not found`);
      return false;
    } else if (!shouldExist && hasStationCode) {
      console.log(`[${scenarioId}]    ❌ FAIL: Unexpected Station Code notification found`);
      console.log(`[${scenarioId}]    Details:`, notifications.filter(n => /station/i.test(n.text)));
      return false;
    } else if (shouldExist) {
      console.log(`[${scenarioId}]    ✅ PASS: Station Code notification present`);
      return true;
    } else {
      console.log(`[${scenarioId}]    ✅ PASS: No Station Code notification (expected)`);
      return true;
    }
  }

  /**
   * Verifies foreground service notification
   */
  static verifyForegroundServiceNotification(scenarioId, notifications) {
    const hasService = notifications.some(n => 
      /not.*green/i.test(n.text) || /climate/i.test(n.text) || /battery.*service/i.test(n.text)
    );

    if (hasService) {
      console.log(`[${scenarioId}]    ✓ Foreground service notification present`);
      return true;
    } else {
      console.log(`[${scenarioId}]    ⚠️  Foreground service notification not visible`);
      return false;
    }
  }

  /**
   * Wait for background processes (Login/Register, getDeviceInfo)
   */
  static async waitForBackgroundProcesses(scenarioId, waitTime = 5000) {
    console.log(`[${scenarioId}] --- Background service (wait ${waitTime / 1000}s) ---`);
    console.log(`[${scenarioId}]   Expected: Login/Register API, getDeviceInfo, BatteryService. Check: logs.`);
    await browser.pause(waitTime);
  }

  /**
   * Verify permission screen is gone before Home screen
   */
  static async verifyPermissionScreenGone(scenarioId, deviceId) {
    console.log(`[${scenarioId}] --- Permission screen gone (ready for Home) ---`);
    console.log(`[${scenarioId}]   Expected: Splash permission UI dismissed so app can show loading → Home.`);

    const requiredPermsTitle = await browser.$(selectors.byTextContains('Required Permissions'));
    const permsStillVisible = await requiredPermsTitle.isDisplayed().catch(() => false);
    if (permsStillVisible) {
      throw new Error('SplashScreen permission UI still visible – app has not finished granting WiFi/Location permissions.');
    }

    const logs = adb.getRecentLogcat(deviceId, 400);
    const locationOk = logs.some((l) => /Location services - GPS:\s*true,\s*Network:\s*true/i.test(l));
    const wifiOk = logs.some((l) => /WiFi Check - SSID:/i.test(l) || /Valid WiFi connection/i.test(l));
    console.log(`[${scenarioId}]   Permission screen gone. Log hint → locationOk=${locationOk}, wifiOk=${wifiOk} (informational; tags may filter these lines).`);
  }

  /**
   * Verify SetClimateStatus API was called (check logs)
   */
  static verifySetClimateStatusCalled(scenarioId, deviceId) {
    console.log(`[${scenarioId}] --- Verify SetClimateStatus called ---`);
    const logs = adb.getRecentLogcat(deviceId, 500);
    const setClimateStatusCalled = logs.some((l) => 
      /setClimateStatus|SetClimateStatus/i.test(l) && 
      (/success|climateStatus=/i.test(l) || /POST.*setclimatestatus/i.test(l))
    );
    
    if (setClimateStatusCalled) {
      console.log(`[${scenarioId}]    ✅ PASS: SetClimateStatus API was called`);
      return true;
    } else {
      console.log(`[${scenarioId}]    ❌ FAIL: SetClimateStatus API was not called`);
      return false;
    }
  }

  /**
   * Extract climate status value from logs
   * @returns {number|null} Climate status value or null if not found
   */
  static extractClimateStatus(scenarioId, deviceId) {
    const logs = adb.getRecentLogcat(deviceId, 500);
    for (const log of logs) {
      // Look for patterns like "climateStatus=4" or "climateStatus: 4"
      const match = log.match(/climateStatus[=:]\s*(\d+)/i);
      if (match) {
        const status = parseInt(match[1], 10);
        console.log(`[${scenarioId}]    ✓ Found climate status: ${status}`);
        return status;
      }
    }
    console.log(`[${scenarioId}]    ⚠️  Climate status not found in logs`);
    return null;
  }

  /**
   * Verify charging animation/text is visible on home screen
   */
  static async verifyChargingAnimation(scenarioId) {
    console.log(`[${scenarioId}] --- Verify charging animation/text ---`);
    console.log(`[${scenarioId}]   Expected: "Charging" text visible on home screen.`);
    
    const chargingText = await browser.$(selectors.byTextContains('Charging'));
    try {
      await chargingText.waitForDisplayed({ timeout: 5000 });
      if (await chargingText.isDisplayed()) {
        console.log(`[${scenarioId}]    ✅ PASS: Charging text/animation visible`);
        return true;
      }
    } catch (e) {
      console.log(`[${scenarioId}]    ⚠️  Charging text not found (may still be loading)`);
    }
    
    return false;
  }
}

module.exports = UIHelper;
