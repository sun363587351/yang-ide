/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.cisco.yangide.ext.refactoring;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.TextFileChange;

/**
 * @author Konstantin Zaitsev
 * @date Jul 31, 2014
 */
public class YangFileChange extends TextFileChange {

    private IDocument document;

    public YangFileChange(String name, IFile file, IDocument document) {
        super(name, file);
        this.document = document;
        setTextType("yang");
    }

    @Override
    protected IDocument acquireDocument(IProgressMonitor pm) throws CoreException {
        IDocument doc = super.acquireDocument(pm);
        if (document != null) {
            doc.set(document.get());
        }
        return doc;
    }
}
