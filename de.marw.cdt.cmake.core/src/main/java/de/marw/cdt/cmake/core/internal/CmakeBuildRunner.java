/*******************************************************************************
 * Copyright (c) 2014 Martin Weber.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package de.marw.cdt.cmake.core.internal;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.cdt.core.ICommandLauncher;
import org.eclipse.cdt.core.IMarkerGenerator;
import org.eclipse.cdt.core.resources.IConsole;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.extension.CBuildData;
import org.eclipse.cdt.managedbuilder.core.AbstractBuildRunner;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.ExternalBuildRunner;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IHoldsOptions;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.IOptionCategory;
import org.eclipse.cdt.managedbuilder.core.IToolChain;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.macros.IFileContextBuildMacroValues;
import org.eclipse.cdt.managedbuilder.macros.IReservedMacroNameSupplier;
import org.eclipse.cdt.managedbuilder.makegen.IManagedBuilderMakefileGenerator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Version;

import de.marw.cdt.cmake.core.CdtPlugin;
import de.marw.cdt.cmake.core.internal.settings.AbstractOsPreferences;
import de.marw.cdt.cmake.core.internal.settings.CMakePreferences;
import de.marw.cdt.cmake.core.internal.settings.ConfigurationManager;
import de.marw.cmake.cmakecache.SimpleCMakeCacheTxt;

/**
 * An ExternalBuildRunner that injects the build tool command to use and some of
 * its arguments into the build. Necessary since CDT does not allow
 * {@code IConfigurationBuildMacroSupplier}s for a Builder (only for
 * tool-chains).
 *
 * @author Martin Weber
 */
public class CmakeBuildRunner extends ExternalBuildRunner {
  private static final ILog log = CdtPlugin.getDefault().getLog();

  /*-
   * @see org.eclipse.cdt.managedbuilder.core.ExternalBuildRunner#invokeBuild(int, org.eclipse.core.resources.IProject, org.eclipse.cdt.managedbuilder.core.IConfiguration, org.eclipse.cdt.managedbuilder.core.IBuilder, org.eclipse.cdt.core.resources.IConsole, org.eclipse.cdt.core.IMarkerGenerator, org.eclipse.core.resources.IncrementalProjectBuilder, org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  public boolean invokeBuild(int kind, IProject project,
      IConfiguration configuration, IBuilder builder, IConsole console,
      IMarkerGenerator markerGenerator,
      IncrementalProjectBuilder projectBuilder, IProgressMonitor monitor)
      throws CoreException {
    /*
     * wrap the passed-in builder into one that gets its build command from the
     * Cmake-generator. First do a sanity check.
     */
    if (builder.getBaseId().equals("de.marw.cdt.cmake.core.genscriptbuilder")) {
      final ICConfigurationDescription cfgd = ManagedBuildManager
          .getDescriptionForConfiguration(configuration);
      final CMakePreferences prefs = ConfigurationManager.getInstance()
          .getOrLoad(cfgd);
      final AbstractOsPreferences osPrefs = AbstractOsPreferences
          .extractOsPreferences(prefs);

      // try to get CMAKE_BUILD_TOOL entry from CMakeCache.txt...
      final CmakeGenerator generator = osPrefs.getGenerator();
      String buildscriptProcessorCmd = getCommandFromCMakeCache(cfgd);
      if (buildscriptProcessorCmd == null) {
        // fall back to values from OS preferences
        buildscriptProcessorCmd = osPrefs.getBuildscriptProcessorCommand();
        if (buildscriptProcessorCmd == null) {
          // fall back to built-in defaults from CMake generator
          buildscriptProcessorCmd = generator.getBuildscriptProcessorCommand();
        }
      }
      builder = new CmakeBuildToolInjectorBuilder(builder,
          buildscriptProcessorCmd, generator);
    }
    return super.invokeBuild(kind, project, configuration, builder, console,
        markerGenerator, projectBuilder, monitor);
  }

  /**
   * Gets the {@code "CMAKE_BUILD_TOOL"} value from the parsed content of the
   * CMake cache file (CMakeCache.txt) corresponding to the specified
   * configuration. If the cache for the parsed content is invalid, tries to
   * parse the CMakeCache.txt file first and then caches the parsed content.
   *
   * @param cfgd
   *        configuration
   * @return a value for the {@code "cmake_build_cmd"} macro or {@code null}, if
   *         none could be determined
   * @throws CoreException
   */
  private String getCommandFromCMakeCache(ICConfigurationDescription cfgd)
      throws CoreException {
    SimpleCMakeCacheTxt cmCache = null;

    // If getBuilderCWD() returns a workspace relative path, it gets garbled by CDT.
    // If garbled, make sure de.marw.cdt.cmake.core.internal.BuildscriptGenerator.getBuildWorkingDir()
    // returns a full, absolute path relative to the workspace.
    final IPath builderCWD = cfgd.getBuildSetting().getBuilderCWD();

    final IFile cmakeCache = ResourcesPlugin.getWorkspace().getRoot()
        .getFile(builderCWD.append("CMakeCache.txt"));
    if (!cmakeCache.exists()) {
      // avoid 'Resource does not exist' exception
      cmakeCache.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
    }
    cmCache = (SimpleCMakeCacheTxt) cmakeCache
        .getSessionProperty(de.marw.cmake.CMakePlugin.CMAKECACHE_PARSED_PROP);
//    System.out.println("have cached CMakeCache: " + (cmCache != null));
    if (cmCache == null) { // must parse CMakeCache.txt

      IPath location = cmakeCache.getLocation();
      if (location == null) {
        return null; // fall back to built-in from generator
      }
      final File file = location.toFile();

      try {
        // parse CMakeCache.txt...
        cmCache = new SimpleCMakeCacheTxt(file);
        // store parsed cache as resource property
        cmakeCache.setSessionProperty(de.marw.cmake.CMakePlugin.CMAKECACHE_PARSED_PROP,
            cmCache);
//        System.out.println("stored cached CMakeCache");
      } catch (IOException ex) {
        // ignore, the build command will run cmake anyway.
        // So let cmake complain about its cache file
        log.log(new Status(IStatus.ERROR, CdtPlugin.PLUGIN_ID,
            "Failed to read file " + file, ex));
      }
    }
    return cmCache == null ? null : cmCache.getBuildTool();
  }

  /**
   * @author Martin Weber
   */
  private static class CmakeBuildToolInjectorBuilder implements IBuilder {
    private final IBuilder delegate;
    private final IPath cmakeBuildTool;
    private final CmakeGenerator generator;

    /**
     * @param delegate
     *        the builder we delegate most of the methods to
     * @param cmakeBuildTool
     *        the buildscript processor command to inject (e.g. 'make')
     * @param generator
     *        the cmake generator that generated the build scripts.
     */
    public CmakeBuildToolInjectorBuilder(IBuilder delegate,
        String cmakeBuildTool, CmakeGenerator generator) {
      this.delegate = delegate;
      this.cmakeBuildTool = new Path(cmakeBuildTool);
      this.generator = generator;
    }

    @Override
    public IPath getBuildCommand() {
      return cmakeBuildTool;
    }

    @Override
    public String getArguments() {
      return this.delegate.getArguments();
    }

    @Override
    public String getBuildArguments() {
      final String args0 = delegate.getBuildArguments();
      String ret = args0;

      final String extraArg = generator.getBuildscriptProcessorExtraArg();
      if (extraArg != null) {
        ret = extraArg;
        if (args0 != null && args0.length() > 0) {
          ret += " ";
          ret += args0;
        }
      }
      // TODO Handle parallel build cmd & ignore err cmd
      return ret;
    }

    @Override
    public String getId() {
      return this.delegate.getId();
    }

    @Override
    public String getName() {
      return this.delegate.getName();
    }

    @Override
    public String getBaseId() {
      return this.delegate.getBaseId();
    }

    @Override
    public Version getVersion() {
      return this.delegate.getVersion();
    }

    @Override
    public void setVersion(Version version) {
      this.delegate.setVersion(version);
    }

    @Override
    public String getManagedBuildRevision() {
      return this.delegate.getManagedBuildRevision();
    }

    @Override
    public IOption createOption(IOption superClass, String Id, String name,
        boolean isExtensionElement) {
      return this.delegate.createOption(superClass, Id, name,
          isExtensionElement);
    }

    @Override
    public void setBuildAttribute(String name, String value)
        throws CoreException {
      this.delegate.setBuildAttribute(name, value);
    }

    @Override
    public String getBuildAttribute(String name, String defaultValue) {
      return this.delegate.getBuildAttribute(name, defaultValue);
    }

    @Override
    public boolean isAutoBuildEnable() {
      return this.delegate.isAutoBuildEnable();
    }

    @Override
    public IPath getBuildLocation() {
      return this.delegate.getBuildLocation();
    }

    @Override
    public void setAutoBuildEnable(boolean enabled) throws CoreException {
      this.delegate.setAutoBuildEnable(enabled);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBuildLocation(IPath location) throws CoreException {
      this.delegate.setBuildLocation(location);
    }

    @Override
    public String getAutoBuildTarget() {
      return this.delegate.getAutoBuildTarget();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setAutoBuildTarget(String target) throws CoreException {
      this.delegate.setAutoBuildTarget(target);
    }

    @Override
    public boolean isStopOnError() {
      return this.delegate.isStopOnError();
    }

    @Override
    public void setStopOnError(boolean on) throws CoreException {
      this.delegate.setStopOnError(on);
    }

    @Override
    public boolean isIncrementalBuildEnabled() {
      return this.delegate.isIncrementalBuildEnabled();
    }

    @Override
    public boolean supportsStopOnError(boolean on) {
      return this.delegate.supportsStopOnError(on);
    }

    @Override
    public void setIncrementalBuildEnable(boolean enabled) throws CoreException {
      this.delegate.setIncrementalBuildEnable(enabled);
    }

    @Override
    public int getParallelizationNum() {
      return this.delegate.getParallelizationNum();
    }

    @Override
    public void removeOption(IOption option) {
      this.delegate.removeOption(option);
    }

    @Override
    public String getIncrementalBuildTarget() {
      return this.delegate.getIncrementalBuildTarget();
    }

    @SuppressWarnings("deprecation")
    @Override
    public IOption getOption(String id) {
      return this.delegate.getOption(id);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setIncrementalBuildTarget(String target) throws CoreException {
      this.delegate.setIncrementalBuildTarget(target);
    }

    @Override
    public void setParallelizationNum(int jobs) throws CoreException {
      this.delegate.setParallelizationNum(jobs);
    }

    @Override
    public boolean isFullBuildEnabled() {
      return this.delegate.isFullBuildEnabled();
    }

    @Override
    public void setFullBuildEnable(boolean enabled) throws CoreException {
      this.delegate.setFullBuildEnable(enabled);
    }

    @Override
    public String getFullBuildTarget() {
      return this.delegate.getFullBuildTarget();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setFullBuildTarget(String target) throws CoreException {
      this.delegate.setFullBuildTarget(target);
    }

    @Override
    public boolean supportsParallelBuild() {
      return this.delegate.supportsParallelBuild();
    }

    @Override
    public String getCleanBuildTarget() {
      return this.delegate.getCleanBuildTarget();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setCleanBuildTarget(String target) throws CoreException {
      this.delegate.setCleanBuildTarget(target);
    }

    @Override
    public IOption getOptionById(String id) {
      return this.delegate.getOptionById(id);
    }

    @Override
    public boolean isParallelBuildOn() {
      return this.delegate.isParallelBuildOn();
    }

    @Override
    public boolean isCleanBuildEnabled() {
      return this.delegate.isCleanBuildEnabled();
    }

    @Override
    public void setCleanBuildEnable(boolean enabled) throws CoreException {
      this.delegate.setCleanBuildEnable(enabled);
    }

    @Override
    public void setParallelBuildOn(boolean on) throws CoreException {
      this.delegate.setParallelBuildOn(on);
    }

    @Override
    public boolean isDefaultBuildCmd() {
      return this.delegate.isDefaultBuildCmd();
    }

    @Override
    public void setUseDefaultBuildCmd(boolean on) throws CoreException {
      this.delegate.setUseDefaultBuildCmd(on);
    }

    @Override
    public IOption getOptionBySuperClassId(String id) {
      return this.delegate.getOptionBySuperClassId(id);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBuildCommand(IPath command) throws CoreException {
      this.delegate.setBuildCommand(command);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBuildArguments(String args) throws CoreException {
      this.delegate.setBuildArguments(args);
    }

    @Override
    public String[] getErrorParsers() {
      return this.delegate.getErrorParsers();
    }

    @Override
    public void setErrorParsers(String[] parsers) throws CoreException {
      this.delegate.setErrorParsers(parsers);
    }

    @Override
    public Map<String, String> getExpandedEnvironment() throws CoreException {
      return this.delegate.getExpandedEnvironment();
    }

    @Override
    public IOption[] getOptions() {
      return this.delegate.getOptions();
    }

    @Override
    public Map<String, String> getEnvironment() {
      return this.delegate.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, String> env) throws CoreException {
      this.delegate.setEnvironment(env);
    }

    @Override
    public boolean appendEnvironment() {
      return this.delegate.appendEnvironment();
    }

    @Override
    public void setAppendEnvironment(boolean append) throws CoreException {
      this.delegate.setAppendEnvironment(append);
    }

    @Override
    public boolean isManagedBuildOn() {
      return this.delegate.isManagedBuildOn();
    }

    @Override
    public void setManagedBuildOn(boolean on) throws CoreException {
      this.delegate.setManagedBuildOn(on);
    }

    @Override
    public IOptionCategory[] getChildCategories() {
      return this.delegate.getChildCategories();
    }

    @Override
    public boolean supportsBuild(boolean managed) {
      return this.delegate.supportsBuild(managed);
    }

    @Override
    public void addOptionCategory(IOptionCategory category) {
      this.delegate.addOptionCategory(category);
    }

    @Override
    public IOptionCategory getOptionCategory(String id) {
      return this.delegate.getOptionCategory(id);
    }

    @Override
    public void createOptions(IHoldsOptions superClass) {
      this.delegate.createOptions(superClass);
    }

    @Override
    public IOption getOptionToSet(IOption option, boolean adjustExtension)
        throws BuildException {
      return this.delegate.getOptionToSet(option, adjustExtension);
    }

    @Override
    public boolean needsRebuild() {
      return this.delegate.needsRebuild();
    }

    @Override
    public void setRebuildState(boolean rebuild) {
      this.delegate.setRebuildState(rebuild);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IConfigurationElement getBuildFileGeneratorElement() {
      return this.delegate.getBuildFileGeneratorElement();
    }

    @Override
    public IManagedBuilderMakefileGenerator getBuildFileGenerator() {
      return this.delegate.getBuildFileGenerator();
    }

    @Override
    public String getCommand() {
      return this.delegate.getCommand();
    }

    @Override
    public String getErrorParserIds() {
      return this.delegate.getErrorParserIds();
    }

    @Override
    public String[] getErrorParserList() {
      return this.delegate.getErrorParserList();
    }

    @Override
    public IToolChain getParent() {
      return this.delegate.getParent();
    }

    @Override
    public IBuilder getSuperClass() {
      return this.delegate.getSuperClass();
    }

    @Override
    public String getUnusedChildren() {
      return this.delegate.getUnusedChildren();
    }

    @Override
    public boolean isAbstract() {
      return this.delegate.isAbstract();
    }

    @Override
    public boolean isDirty() {
      return this.delegate.isDirty();
    }

    @Override
    public boolean isExtensionElement() {
      return this.delegate.isExtensionElement();
    }

    @Override
    public void setArguments(String makeArgs) {
      this.delegate.setArguments(makeArgs);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBuildFileGeneratorElement(IConfigurationElement element) {
      this.delegate.setBuildFileGeneratorElement(element);
    }

    @Override
    public void setCommand(String command) {
      this.delegate.setCommand(command);
    }

    @Override
    public void setDirty(boolean isDirty) {
      this.delegate.setDirty(isDirty);
    }

    @Override
    public void setErrorParserIds(String ids) {
      this.delegate.setErrorParserIds(ids);
    }

    @Override
    public void setIsAbstract(boolean b) {
      this.delegate.setIsAbstract(b);
    }

    @Override
    public String getVersionsSupported() {
      return this.delegate.getVersionsSupported();
    }

    @Override
    public String getConvertToId() {
      return this.delegate.getConvertToId();
    }

    @Override
    public void setVersionsSupported(String versionsSupported) {
      this.delegate.setVersionsSupported(versionsSupported);
    }

    @Override
    public void setConvertToId(String convertToId) {
      this.delegate.setConvertToId(convertToId);
    }

    @Override
    public IFileContextBuildMacroValues getFileContextBuildMacroValues() {
      return this.delegate.getFileContextBuildMacroValues();
    }

    @Override
    public String getBuilderVariablePattern() {
      return this.delegate.getBuilderVariablePattern();
    }

    @Override
    public boolean isVariableCaseSensitive() {
      return this.delegate.isVariableCaseSensitive();
    }

    @Override
    public String[] getReservedMacroNames() {
      return this.delegate.getReservedMacroNames();
    }

    @Override
    public IReservedMacroNameSupplier getReservedMacroNameSupplier() {
      return this.delegate.getReservedMacroNameSupplier();
    }

    @Override
    public CBuildData getBuildData() {
      return this.delegate.getBuildData();
    }

    @Override
    public boolean isCustomBuilder() {
      return this.delegate.isCustomBuilder();
    }

    @Override
    public boolean supportsCustomizedBuild() {
      return this.delegate.supportsCustomizedBuild();
    }

    @Override
    public boolean keepEnvironmentVariablesInBuildfile() {
      return this.delegate.keepEnvironmentVariablesInBuildfile();
    }

    @Override
    public void setKeepEnvironmentVariablesInBuildfile(boolean keep) {
      this.delegate.setKeepEnvironmentVariablesInBuildfile(keep);
    }

    @Override
    public boolean canKeepEnvironmentVariablesInBuildfile() {
      return this.delegate.canKeepEnvironmentVariablesInBuildfile();
    }

    @Override
    public void setBuildPath(String path) {
      this.delegate.setBuildPath(path);
    }

    @Override
    public String getBuildPath() {
      return this.delegate.getBuildPath();
    }

    @Override
    public boolean isInternalBuilder() {
      return this.delegate.isInternalBuilder();
    }

    @Override
    public boolean matches(IBuilder builder) {
      return this.delegate.matches(builder);
    }

    @Override
    public boolean isSystemObject() {
      return this.delegate.isSystemObject();
    }

    @Override
    public String getUniqueRealName() {
      return this.delegate.getUniqueRealName();
    }

    @Override
    public ICommandLauncher getCommandLauncher() {
      return this.delegate.getCommandLauncher();
    }

    @Override
    public AbstractBuildRunner getBuildRunner() throws CoreException {
      return this.delegate.getBuildRunner();
    }

  }
}
