/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import net.minecraftforge.gradle.common.tasks.ExtractNatives;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EclipseHacks {

    public static void doEclipseFixes(final MinecraftExtension minecraft, final TaskProvider<ExtractNatives> nativesTask, final List<? extends TaskProvider<?>> setupTasks) {
        final Project project = minecraft.getProject();
        final Provider<File> natives = nativesTask.flatMap(s -> s.getOutput().getAsFile());

        final EclipseModel eclipseConv = (EclipseModel)project.getExtensions().findByName("eclipse");
        if (eclipseConv == null) {
            // The eclipse plugin hasn't been applied; we don't need to do any eclipse things
            return;
        }
        final XmlFileContentMerger classpathMerger = eclipseConv.getClasspath().getFile();

        final String LIB_ATTR = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";

        project.getTasks().withType(GenerateEclipseClasspath.class).configureEach(task -> {
            task.dependsOn(nativesTask, setupTasks);
        });

        classpathMerger.whenMerged(obj -> {
            Classpath classpath = (Classpath)obj;
            Set<String> paths = new HashSet<>();
            Iterator<ClasspathEntry> itr = classpath.getEntries().iterator();
            while (itr.hasNext()) {
                ClasspathEntry entry = itr.next();
                if (entry instanceof SourceFolder) {
                    SourceFolder sf = (SourceFolder)entry;
                    if (!paths.add(sf.getPath())) {
                        //Eclipse likes to duplicate things... No idea why, let's kill them off
                        itr.remove();
                        continue;
                    }

                    if (!sf.getEntryAttributes().containsKey(LIB_ATTR)) {
                        sf.getEntryAttributes().put(LIB_ATTR, natives.get().getAbsolutePath());
                    }
                }
            }
        });
    }
}
