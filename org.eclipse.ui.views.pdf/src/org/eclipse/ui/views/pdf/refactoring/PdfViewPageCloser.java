package org.eclipse.ui.views.pdf.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.views.file.FileView;
import org.eclipse.ui.views.file.IFileViewType;
import org.eclipse.ui.views.pdf.Activator;
import org.eclipse.ui.views.pdf.PdfViewType;
import org.eclipse.util.UiUtils;

public class PdfViewPageCloser {

	private PdfViewPageCloser() {}

	public static void releaseOpenedFiles(Object fileOrFolder) {
		List<IFile> filesToRelease = getFilesToRelease(fileOrFolder);
		if(!filesToRelease.isEmpty()){
			IViewReference[] views = UiUtils.getWorkbenchPage().getViewReferences();
			for (IViewReference ref : views) {
				IViewPart view = ref.getView(false);
				if (view instanceof FileView) {
					FileView fileView = (FileView)view;
					IFileViewType<?> fileViewType = fileView.getType();
					if (fileViewType instanceof PdfViewType) {
						PdfViewType pdfViewType = (PdfViewType)fileViewType;
						Display.getDefault().syncExec(new Runnable(){
							@Override
							public void run() {
								for (IFile fileToRelease : filesToRelease) {
									pdfViewType.release(fileToRelease);
								}
							}
						});
					}
				}
			}
		}
	}

	private static List<IFile> getFilesToRelease(Object element){
		final List<IFile> result=new ArrayList<IFile>();
		if(element instanceof IFile){
			IFile file = (IFile)element;
			if(isPdf(file)){
				result.add(file);
			}
		}else if(element instanceof IFolder){
			try {
				((IFolder)element).accept(new IResourceVisitor() {
					@Override
					public boolean visit(IResource resource) {
						if (resource instanceof IFile) {
							IFile file = (IFile)resource;
							if(isPdf(file)){
								result.add(file);
							}
						}
						return true;
					}
				});
			} catch (CoreException e) {
				Activator.logError("error traversing folder to delete", e);
			}
		}
		return result;
	}

	private static boolean isPdf(IFile file){
		return file.getFileExtension().equals(PdfViewType.EXTENSION);
	}

}
