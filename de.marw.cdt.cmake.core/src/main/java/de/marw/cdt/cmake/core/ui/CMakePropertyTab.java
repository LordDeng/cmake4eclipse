/*******************************************************************************
 * Copyright (c) 2013-2014 Martin Weber.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package de.marw.cdt.cmake.core.ui;

import java.util.BitSet;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICMultiConfigDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.core.settings.model.ICStorageElement;
import org.eclipse.cdt.ui.newui.AbstractCPropertyTab;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;

import de.marw.cdt.cmake.core.CMakePlugin;
import de.marw.cdt.cmake.core.internal.settings.CMakePreferences;

/**
 * UI to control settings and preferences for cmake. This tab is responsible for
 * storing its values.
 *
 * @author Martin Weber
 */
public class CMakePropertyTab extends AbstractCPropertyTab {

  /**  */
  private static final ILog log = CMakePlugin.getDefault().getLog();

  // Widgets
  //1
  private Button b_warnNoDev;
  private Button b_debug;
  private Button b_trace;
  private Button b_warnUnitialized;
  private Button b_warnUnused;

  /** the configuration we manage here. Initialized in {@link #updateData} */
  private ICConfigurationDescription cfgd;
  /**
   * the preferences associated with our configurations to manage. Initialized
   * in {@link #updateData}
   */
  private CMakePreferences[] prefs;

  /** shared listener for check-boxes */
  private TriStateButtonListener tsl = new TriStateButtonListener();

  @Override
  protected void createControls(Composite parent) {
    super.createControls(parent);
    usercomp.setLayout(new GridLayout(2, false));
//    usercomp.setBackground(BACKGROUND_FOR_USER_VAR);

    // cmake options group...
    {
      Group gr = WidgetHelper.createGroup(usercomp, SWT.FILL, 2, "Commandline Options", 2);

      b_warnNoDev = WidgetHelper.createCheckbox(gr,
          SWT.BEGINNING, 2, "Suppress developer warnings (-Wno-dev)");
      b_warnNoDev.addListener(SWT.Selection, tsl);
      b_debug = WidgetHelper.createCheckbox(gr,
          SWT.BEGINNING, 2, "Put cmake in a debug mode (--debug-output)");
      b_debug.addListener(SWT.Selection, tsl);
      b_trace = WidgetHelper.createCheckbox(gr, SWT.BEGINNING,
          2, "Put cmake in trace mode (--trace)");
      b_trace.addListener(SWT.Selection, tsl);
      b_warnUnitialized = WidgetHelper.createCheckbox(gr,
          SWT.BEGINNING,
          2, "Warn about uninitialized values (--warn-uninitialized)");
      b_warnUnitialized.addListener(SWT.Selection, tsl);
      b_warnUnused = WidgetHelper.createCheckbox(gr,
          SWT.BEGINNING, 2, "Warn about unused variables (--warn-unused-vars)");
      b_warnUnused.addListener(SWT.Selection, tsl);
    } // cmake options group

  }

  /*-
   * @see org.eclipse.cdt.ui.newui.AbstractCPropertyTab#updateData(org.eclipse.cdt.core.settings.model.ICResourceDescription)
   */
  @Override
  protected void updateData(ICResourceDescription resd) {
    if (resd == null)
      return;

    cfgd = resd.getConfiguration();
    try {
      if (cfgd instanceof ICMultiConfigDescription) {
        // we are editing multiple configurations...
        ICConfigurationDescription[] cfgs = (ICConfigurationDescription[]) ((ICMultiConfigDescription) cfgd)
            .getItems();

        prefs = new CMakePreferences[cfgs.length];
        for (int i = 0; i < cfgs.length; i++) {
          ICConfigurationDescription cfg = cfgs[i];
          CMakePreferences pref = new CMakePreferences();
          ICStorageElement storage = cfg.getStorage(
              CMakePreferences.CFG_STORAGE_ID, false);
          pref.loadFromStorage(storage, false);

          prefs[i] = pref;
        }
      } else {
        // we are editing a single configuration...
        prefs = new CMakePreferences[1];
        CMakePreferences pref = prefs[0] = new CMakePreferences();
        ICStorageElement storage = cfgd.getStorage(
            CMakePreferences.CFG_STORAGE_ID, false);
        pref.loadFromStorage(storage, false);
      }
    } catch (CoreException ex) {
      log.log(new Status(IStatus.ERROR, CMakePlugin.PLUGIN_ID, null, ex));
    }
    updateDisplay();
  }

  /**
   * Updates displayed values according to the preferences edited by this tab.
   */
  private void updateDisplay() {
    if (prefs.length > 1) {
      // we are editing multiple configurations...
      /*
       * make each button tri-state, if its settings are not the same in all
       * configurations
       */
      BitSet bs = new BitSet(prefs.length);
      // b_warnNoDev...
      for (int i = 0; i < prefs.length; i++) {
        bs.set(i, prefs[i].isWarnNoDev());
      }
      enterTristateOrToggleMode(b_warnNoDev, bs, prefs.length);

      // b_debug...
      bs.clear();
      for (int i = 0; i < prefs.length; i++) {
        bs.set(i, prefs[i].isDebugOutput());
      }
      enterTristateOrToggleMode(b_debug, bs, prefs.length);

      // b_trace...
      bs.clear();
      for (int i = 0; i < prefs.length; i++) {
        bs.set(i, prefs[i].isTrace());
      }
      enterTristateOrToggleMode(b_trace, bs, prefs.length);

      // b_warnUnitialized...
      bs.clear();
      for (int i = 0; i < prefs.length; i++) {
        bs.set(i, prefs[i].isWarnUnitialized());
      }
      enterTristateOrToggleMode(b_warnUnitialized, bs, prefs.length);

      // b_warnUnused...
      bs.clear();
      for (int i = 0; i < prefs.length; i++) {
        bs.set(i, prefs[i].isWarnUnused());
      }
      enterTristateOrToggleMode(b_warnUnused, bs, prefs.length);
    } else {
      // we are editing a single configuration...
      // all buttons are in toggle mode
      CMakePreferences pref = prefs[0];
      enterToggleMode(b_warnNoDev, pref.isWarnNoDev());
      enterToggleMode(b_debug, pref.isDebugOutput());
      enterToggleMode(b_trace, pref.isTrace());
      enterToggleMode(b_warnUnitialized, pref.isWarnUnitialized());
      enterToggleMode(b_warnUnused, pref.isWarnUnused());
    }
  }

  /**
   * Switches the specified button behavior from tri-state mode to toggle mode.
   *
   * @param button
   *        the button to modify
   * @param buttonSelected
   *        the selection of the button
   */
  private void enterToggleMode(Button button, boolean buttonSelected) {
    button.setData(null); // mark toggle mode
    button.setSelection(buttonSelected);
    button.setGrayed(false);
  }

  /**
   * Switches the specified button behavior from toggle mode to tri-state mode.
   *
   * @param button
   *        the button to modify
   */
  private void enterTristateOrToggleMode(Button button, BitSet bs, int numBits) {
    if (needsTri(bs, numBits)) {
      enterTristateMode(button);
    } else {
      enterToggleMode(button, !bs.isEmpty());
    }
  }

  /**
   * Switches the specified button behavior to toggle mode or to tri-state mode.
   *
   * @param button
   *        the button to modify
   */
  private void enterTristateMode(Button button) {
    button.setData(Boolean.TRUE); // mark in tri-state mode
    button.setSelection(true);
    button.setGrayed(true);
  }

  /**
   * Gets whether all bits in the bit set have the same state.
   */
  private boolean needsTri(BitSet bs, int numBits) {
    final int card = bs.cardinality();
    return !(card == numBits || card == 0);
  }

  /**
   * Invoked when project configuration changes??
   *
   * @see org.eclipse.cdt.ui.newui.AbstractCPropertyTab#performApply(org.eclipse.cdt.core.settings.model.ICResourceDescription,
   *      org.eclipse.cdt.core.settings.model.ICResourceDescription)
   */
  @Override
  protected void performApply(ICResourceDescription src,
      ICResourceDescription dst) {
    ICConfigurationDescription srcCfg = src.getConfiguration();
    ICConfigurationDescription dstCfg = dst.getConfiguration();

    if (srcCfg instanceof ICMultiConfigDescription) {
      ICConfigurationDescription[] srcCfgs = (ICConfigurationDescription[]) ((ICMultiConfigDescription) src)
          .getItems();
      ICConfigurationDescription[] dstCfgs = (ICConfigurationDescription[]) ((ICMultiConfigDescription) dst)
          .getItems();
      for (int i = 0; i < srcCfgs.length; i++) {
        applyConfig(srcCfgs[i], dstCfgs[i]);
      }
    } else {
      applyConfig(srcCfg, dstCfg);
    }
  }

  /**
   * @param srcCfg
   * @param dstCfg
   */
  private static void applyConfig(ICConfigurationDescription srcCfg,
      ICConfigurationDescription dstCfg) {
    try {
      ICStorageElement srcEl = srcCfg.getStorage(
          CMakePreferences.CFG_STORAGE_ID, false);
      if (srcEl != null) {
        CMakePreferences prefs = new CMakePreferences();
        prefs.loadFromStorage(srcEl, false);

        ICStorageElement dstEl = dstCfg.getStorage(
            CMakePreferences.CFG_STORAGE_ID, true);
        prefs.saveToStorage(dstEl);
      }
    } catch (CoreException ex) {
      log.log(new Status(IStatus.ERROR, CMakePlugin.PLUGIN_ID, null, ex));
    }
  }

  protected void performOK() {
    // save as project settings..
    try {
      if (prefs.length > 1) {
        // we are editing multiple configurations...
        ICConfigurationDescription[] cfgs = (ICConfigurationDescription[]) ((ICMultiConfigDescription) cfgd)
            .getItems();

        for (int i = 0; i < prefs.length; i++) {
          ICConfigurationDescription cfg = cfgs[i];
          CMakePreferences pref = prefs[i];

          if (shouldSaveButtonSelection(b_warnNoDev))
            pref.setWarnNoDev(b_warnNoDev.getSelection());
          if (shouldSaveButtonSelection(b_debug))
            pref.setDebugOutput(b_debug.getSelection());
          if (shouldSaveButtonSelection(b_trace))
            pref.setTrace(b_trace.getSelection());
          if (shouldSaveButtonSelection(b_warnUnitialized))
            pref.setWarnUnitialized(b_warnUnitialized.getSelection());
          if (shouldSaveButtonSelection(b_warnUnused))
            pref.setWarnUnused(b_warnUnused.getSelection());

          ICStorageElement storage = cfg.getStorage(
              CMakePreferences.CFG_STORAGE_ID, true);
          pref.saveToStorage(storage);
        }
      } else {
        // we are editing a single configuration...
        CMakePreferences pref = prefs[0];
        pref.setWarnNoDev(b_warnNoDev.getSelection());
        pref.setDebugOutput(b_debug.getSelection());
        pref.setTrace(b_trace.getSelection());
        pref.setWarnUnitialized(b_warnUnitialized.getSelection());
        pref.setWarnUnused(b_warnUnused.getSelection());

        ICStorageElement storage = cfgd.getStorage(
            CMakePreferences.CFG_STORAGE_ID, true);
        pref.saveToStorage(storage);
      }
    } catch (CoreException ex) {
      log.log(new Status(IStatus.ERROR, CMakePlugin.PLUGIN_ID, null, ex));
    }
  }

  /**
   * Gets whether the value of the specified button should be saved.
   *
   * @param button
   *        the button to query
   */
  private boolean shouldSaveButtonSelection(Button button) {
    if (button.getData() == Boolean.TRUE && button.getGrayed()) {
      // if button is in tri-state mode and grayed, do not save
      return false;
    }
    return true;
  }

  /*-
   * @see org.eclipse.cdt.ui.newui.AbstractCPropertyTab#performDefaults()
   */
  @Override
  protected void performDefaults() {
    for (CMakePreferences pref : prefs) {
      pref.reset();
    }
    updateDisplay();
  }

  // This page can be displayed for project
  @Override
  public boolean canBeVisible() {
    return page.isForProject();
  }

  @Override
  protected void updateButtons() {
    // never called from superclass, but abstract :-)
  }

  ////////////////////////////////////////////////////////////////////
  // inner classes
  ////////////////////////////////////////////////////////////////////
  /**
   * Adds tri-state behavior to a button when added as a SWT.Selection listener.
   *
   * @author Martin Weber
   */
  private static class TriStateButtonListener implements Listener {

    /*-
     * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
     */
    @Override
    public void handleEvent(Event event) {
      final Button btn = (Button) event.widget;
      if (btn.getData() == Boolean.TRUE) {
        // button is in tri-state mode
        if (btn.getSelection()) {
          if (!btn.getGrayed()) {
            btn.setGrayed(true);
          }
        } else {
          if (btn.getGrayed()) {
            btn.setGrayed(false);
            btn.setSelection(true);
          }
        }
      }
    }
  } // TriStateButtonListener
}
