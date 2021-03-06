/*******************************************************************************
 * Copyright (c) 2015-2017 Martin Weber.
 *
 * Content is provided to you under the terms and conditions of the Eclipse Public License Version 2.0 "EPL".
 * A copy of the EPL is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package de.marw.cmake.cdt.internal;

import java.text.MessageFormat;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * @author Martin Weber
 */
public class Plugin extends AbstractUIPlugin {

  public static final String PLUGIN_ID = "de.marw.cmake.cdt"; //$NON-NLS-1$

  //The shared instance.
  private static Plugin plugin;

  /**
   * The constructor.
   */
  public Plugin() {
  }

  /**
   * This method is called upon plug-in activation
   */
  public void start(BundleContext context) throws Exception {
    super.start(context);
    if (!PLUGIN_ID.equals(this.getBundle().getSymbolicName()))
      throw new RuntimeException(
          "BUG: PLUGIN_ID does not match Bundle-SymbolicName");
    plugin = this;
  }

  /**
   * This method is called when the plug-in is stopped
   */
  public void stop(BundleContext context) throws Exception {
    super.stop(context);
    plugin = null;
  }

  /**
   * Returns the shared instance.
   */
  public static Plugin getDefault() {
    return plugin;
  }

  /**
   * Returns the string from the plugin's resource bundle, or 'key' if not
   * found.
   *
   * @param key
   *        the message key
   * @return the resource bundle message
   */
  public static String getResourceString(String key) {
//		ResourceBundle bundle = Plugin.getDefault().getResourceBundle();
//		try {
//			return bundle.getString(key);
//		} catch (MissingResourceException e) {
//			return key;
//		}
    return key;
  }

  /**
   * Returns the string from the plugin's resource bundle, or 'key' if not
   * found.
   *
   * @param key
   *        the message key
   * @param args
   *        an array of substitution strings
   * @return the resource bundle message
   */
  public static String getFormattedString(String key, String[] args) {
    return MessageFormat.format(getResourceString(key), (Object[]) args);
  }
}
