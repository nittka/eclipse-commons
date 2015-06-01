package org.eclipse.ui.views.pdf;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.DeleteParticipant;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.views.file.FileView;
import org.eclipse.ui.views.file.IFileViewType;
import org.eclipse.util.UiUtils;

public class DeletePdfParticipant extends DeleteParticipant {

	@Override
	protected boolean initialize(Object element) {
		List<IFile> filesToDelete = getFilesToDelete(element);
		if(!filesToDelete.isEmpty()){
			IViewReference[] views = UiUtils.getWorkbenchPage().getViewReferences();
			for (IViewReference ref : views) {
				IViewPart view = ref.getView(false);
				if (view instanceof FileView) {
					FileView fileView = (FileView)view;
					IFileViewType<?> fileViewType = fileView.getType();
					if (fileViewType instanceof PdfViewType) {
						PdfViewType pdfViewType = (PdfViewType)fileViewType;
						for (IFile fileToDelete : filesToDelete) {
							pdfViewType.prepareDelete(fileToDelete);
						}
					}
				}
			}
		}
		return false;
	}

	private List<IFile> getFilesToDelete(Object element){
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

	private boolean isPdf(IFile file){
		return file.getFileExtension().equals(PdfViewType.EXTENSION);
	}

	@Override
	public String getName() {
		return "Close Score View";
	}

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context) throws OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		return null;
	}
}
