/*******************************************************************************
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *  
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *  
 *******************************************************************************/
package com.cisco.yangide.core.indexing;

import java.io.IOException;
import java.util.HashSet;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaProject;

import com.cisco.yangide.core.CoreUtil;
import com.cisco.yangide.core.YangCorePlugin;
import com.cisco.yangide.core.YangModelException;
import com.cisco.yangide.core.model.YangProjectInfo;

/**
 * @author Konstantin Zaitsev
 * @date Jul 1, 2014
 */
@SuppressWarnings("restriction")
public class IndexAllProject extends IndexRequest {

    private IProject project;

    public IndexAllProject(IProject project, IndexManager manager) {
        super(project.getFullPath(), manager);
        this.project = project;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IndexAllProject) {
            return this.project.equals(((IndexAllProject) o).project);
        }
        return false;
    }

    @Override
    public boolean execute(IProgressMonitor progressMonitor) {
        System.err.println("[I] Project: " + project.getName());

        if (this.isCancelled || progressMonitor != null && progressMonitor.isCanceled()) {
            return true;
        }

        if (!this.project.isAccessible()) {
            return true;
        }
        final HashSet<IPath> ignoredPath = new HashSet<IPath>();
        final HashSet<IPath> externalJarsPath = new HashSet<IPath>();
        try {
            JavaProject proj = (JavaProject) JavaCore.create(project);
            final HashSet<String> projectScope = new HashSet<>();
            projectScope.add(project.getName());

            if (proj != null) {
                IClasspathEntry[] classpath = proj.getResolvedClasspath();
                for (int i = 0, length = classpath.length; i < length; i++) {
                    IClasspathEntry entry = classpath[i];
                    IPath entryPath = entry.getPath();
                    IPath output = entry.getOutputLocation();
                    if (output != null && !entryPath.equals(output)) {
                        ignoredPath.add(output);
                    }

                    // index dependencies projects
                    if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                        IProject prj = ResourcesPlugin.getWorkspace().getRoot()
                                .getProject(entry.getPath().lastSegment());
                        if (prj != null && prj.exists()) {
                            this.manager.indexAll(prj);
                            projectScope.add(prj.getName());
                        }
                    }
                }
                IPackageFragmentRoot[] roots = proj.getAllPackageFragmentRoots();
                for (int i = 0, length = roots.length; i < length; i++) {
                    IPath entryPath = roots[i].getPath();
                    if (entryPath != null && entryPath.toFile().exists()
                            && entryPath.lastSegment().toLowerCase().endsWith(".jar")) {
                        externalJarsPath.add(entryPath);
                    }
                }
                // Update project information with set of project dependencies
                YangProjectInfo yangProjectInfo = (YangProjectInfo) YangCorePlugin.create(project).getElementInfo(null);
                yangProjectInfo.setProjectScope(projectScope);
                // fill indirect scope
                HashSet<String> indirectScope = new HashSet<String>();
                indirectScope.add(project.getName());
                for (IJavaProject jproj : JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()).getJavaProjects()) {
                    if (jproj != proj) {
                        for (String name : jproj.getRequiredProjectNames()) {
                            if (name.equals(project.getName())) {
                                indirectScope.add(jproj.getProject().getName());
                            }
                        }
                    }
                }
                yangProjectInfo.setIndirectScope(indirectScope);
            }
        } catch (JavaModelException | YangModelException e) {
            // java project doesn't exist: ignore
        }

        for (IPath iPath : externalJarsPath) {
            try (JarFile jarFile = new JarFile(iPath.toFile())) {
                ZipEntry entry = jarFile.getEntry("META-INF/yang/");
                if (entry != null) {
                    this.manager.addJarFile(project, iPath);
                }
            } catch (IOException e) {
                YangCorePlugin.log(e);
            }
        }
        try {
            final HashSet<IFile> indexedFiles = new HashSet<IFile>();
            project.accept(new IResourceProxyVisitor() {
                @Override
                public boolean visit(IResourceProxy proxy) {
                    if (IndexAllProject.this.isCancelled) {
                        return false;
                    }
                    if (!ignoredPath.isEmpty() && ignoredPath.contains(proxy.requestFullPath())) {
                        return false;
                    }
                    if (proxy.getType() == IResource.FILE) {
                        if (CoreUtil.isYangLikeFileName(proxy.getName())) {
                            IFile file = (IFile) proxy.requestResource();
                            indexedFiles.add(file);
                        }
                        return false;
                    }
                    return true;
                }
            }, IResource.NONE);

            for (IFile iFile : indexedFiles) {
                this.manager.addSource(iFile);
            }
        } catch (CoreException e) {
            this.manager.removeIndexFamily(project);
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return this.project.hashCode();
    }

    @Override
    public String toString() {
        return "indexing project " + this.project.getFullPath(); //$NON-NLS-1$
    }
}
