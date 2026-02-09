/**
 * Selector helpers for Android UI automation
 * Provides common Android UiSelector patterns for Appium
 */

const ANDROID_SELECTOR = 'android';

/**
 * Find element by text contains
 */
function byTextContains(text) {
  return `${ANDROID_SELECTOR}=new UiSelector().textContains("${text}")`;
}

/**
 * Only matches clickable buttons (e.g. system permission dialog), not in-app labels.
 */
function byButtonTextContains(text) {
  return `${ANDROID_SELECTOR}=new UiSelector().className("android.widget.Button").textContains("${text}")`;
}

/**
 * Clickable element with text (fallback for permission controller which may not use Button).
 */
function byClickableTextContains(text) {
  return `${ANDROID_SELECTOR}=new UiSelector().clickable(true).textContains("${text}")`;
}

/**
 * System permission dialog Allow - try permission controller first, then package installer.
 */
function byPermissionAllowButton() {
  return `${ANDROID_SELECTOR}=new UiSelector().resourceId("com.android.permissioncontroller:id/permission_allow_button")`;
}

function byPermissionAllowButtonLegacy() {
  return `${ANDROID_SELECTOR}=new UiSelector().resourceId("com.android.packageinstaller:id/permission_allow_button")`;
}

function byResourceId(id) {
  return `${ANDROID_SELECTOR}=new UiSelector().resourceId("${id}")`;
}

function byCheckable(checked = false) {
  return `${ANDROID_SELECTOR}=new UiSelector().checkable(true).checked(${checked})`;
}

module.exports = {
  byTextContains,
  byButtonTextContains,
  byClickableTextContains,
  byPermissionAllowButton,
  byPermissionAllowButtonLegacy,
  byResourceId,
  byCheckable,
  ANDROID_SELECTOR,
};
