/*******************************************************************************
 * Copyright (c) 2019 Martin Weber.
 *
 * Content is provided to you under the terms and conditions of the Eclipse Public License Version 2.0 "EPL".
 * A copy of the EPL is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package de.marw.cmake.cdt.internal.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.core.runtime.Platform;

import de.marw.cmake.cdt.internal.Plugin;
import de.marw.cmake.cdt.lsp.IArglet;
import de.marw.cmake.cdt.lsp.IToolCommandlineParser;

/**
 * Default implementation of IParseContext.
 *
 * @author Martin Weber
 */
public class ParseContext implements IArglet.IParseContext, IToolCommandlineParser.IResult {
  private static final boolean DEBUG = Boolean
      .parseBoolean(Platform.getDebugOption(Plugin.PLUGIN_ID + "/CECC/entries"));
  private final List<ICLanguageSettingEntry> entries = new ArrayList<>();
  private final List<String> args = new ArrayList<>();

  @Override
  public void addSettingEntry(ICLanguageSettingEntry entry) {
    if (DEBUG)
      System.out.printf("    Added entry: %s%n", entry);
    entries.add(entry);
  }

  @Override
  public void addBuiltinDetectionArgument(String argument) {
    args.add(argument);
  }

  @Override
  public List<ICLanguageSettingEntry> getSettingEntries() {
    return entries;
  }

  @Override
  public List<String> getBuiltinDetectionArgs() {
    return args;
  }

}
