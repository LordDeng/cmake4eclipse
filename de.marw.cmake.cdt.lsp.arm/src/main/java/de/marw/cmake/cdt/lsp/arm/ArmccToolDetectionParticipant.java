/*******************************************************************************
 * Copyright (c) 2019 Martin Weber.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/

package de.marw.cmake.cdt.lsp.arm;

import de.marw.cmake.cdt.lsp.Arglets;
import de.marw.cmake.cdt.lsp.DefaultToolCommandlineParser;
import de.marw.cmake.cdt.lsp.DefaultToolDetectionParticipant;
import de.marw.cmake.cdt.lsp.IArglet;

/**
 * armcc C & C++.
 *
 * @author Martin Weber
 */
public class ArmccToolDetectionParticipant extends DefaultToolDetectionParticipant {

  public ArmccToolDetectionParticipant() {
    super("armcc", true, "exe", new ToolCommandlineParser());
  }

  private static class ToolCommandlineParser extends DefaultToolCommandlineParser {

    private static final IArglet[] arglets = {
        new Arglets.IncludePath_C_POSIX(),
        new Arglets.MacroDefine_C_POSIX(), new Arglets.MacroUndefine_C_POSIX(),
        new Arglets.SystemIncludePath_armcc()
    };

    private ToolCommandlineParser() {
      super(null, null, new ArmccBuiltinDetectionBehavior(), arglets);
    }
  }
}
