
package org.eclipse.ui.views.pdf;

import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.util.ImageUtils;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.views.pdf.PdfViewToolbarManager.FitToAction;
import org.jpedal.PdfDecoder;
import org.jpedal.exception.PdfException;
import org.jpedal.objects.acroforms.rendering.AcroRenderer;
import org.jpedal.objects.raw.FormObject;
import org.jpedal.objects.raw.PdfArrayIterator;
import org.jpedal.objects.raw.PdfDictionary;
import org.jpedal.objects.raw.PdfObject;

public class PdfViewPage extends ScrolledComposite {

	/**
	 * The composite which is responsible for the center alignment.
	 */
	private Composite outerContainer;

	/**
	 * The composite which contains the PDF and the hyperlink layer.
	 */
	private Composite innerContainer;

	/**
	 * The label displaying the current page of the PDF file.
	 */
	private Label pdfDisplay;

	public PdfViewPage(Composite parent, IFile file) throws PdfException {
		super(parent, SWT.V_SCROLL | SWT.H_SCROLL);
		setExpandHorizontal(true);
		setExpandVertical(true);
		getVerticalBar().setIncrement(getVerticalBar().getIncrement() * 4);
		getHorizontalBar().setIncrement(getHorizontalBar().getIncrement() * 4);

		outerContainer = new Composite(this, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		outerContainer.setLayout(layout);
		setContent(outerContainer);

		innerContainer = new Composite(outerContainer, SWT.NONE);
		innerContainer.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

		pdfDisplay = new Label(innerContainer, SWT.CENTER);

		hyperlinks = new Composite(innerContainer, SWT.TRANSPARENT | SWT.NO_BACKGROUND); // Both styles are required for correct transparency
		hyperlinks.moveAbove(pdfDisplay);

		this.file = file;
		reload();
	}

	/**
	 * The PDF engine which renders the pages.
	 */
	private final PdfDecoder pdfDecoder = new PdfDecoder();

	@Override
	public void redraw() {
		if (isFileOpen()) {
			pdfDecoder.setPageParameters(getZoom(), getPage());
			try {
				BufferedImage awtImage = pdfDecoder.getPageAsImage(getPage());
				Image swtImage = new Image(Display.getDefault(), ImageUtils.convertBufferedImageToImageData(awtImage));
				pdfDisplay.setImage(swtImage);
				refreshLayout();
			} catch (PdfException e) {
				Activator.logError("Can't redraw PDF page", e);
			}
			createHyperlinks();
			refreshToolbar();
		}
	}

	/**
	 * Whenever the page size changes, this method has to be called to achieve the
	 * correct layout.
	 */
	private void refreshLayout() {
		Point size = pdfDisplay.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		pdfDisplay.setBounds(0, 0, size.x, size.y);
		hyperlinks.setBounds(0, 0, size.x, size.y);
		outerContainer.layout();
		setMinSize(size);
	}

	/**
	 * The open PDF file.
	 */
	private IFile file;

	public IFile getFile() {
		return file;
	}

	public void setFile(IFile file) throws PdfException {
		pdfDecoder.openPdfFile(file.getLocation().toOSString());
		loadAnnotations();
		if (file.equals(this.file)) {
			setPage(getPage());
		} else {
			this.file = file;
			setPage(1);
		}
	}

	public void reload() throws PdfException {
		setFile(getFile());
	}

	public void closeFile() {
		pdfDecoder.closePdfFile();
	}

	public boolean isFileOpen() {
		return pdfDecoder.isOpen();
	}

	/**
	 * The number of the currently viewed page, 1-based.
	 */
	private int page = 1;

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		if (page > getPageCount()) {
			this.page = getPageCount();
		} else if (page < 1) {
			this.page = 1;
		} else {
			this.page = page;
		}
		redraw();
	}

	/**
	 * Returns the number of pages in the PDF file.
	 */
	public int getPageCount() {
		return pdfDecoder.getPageCount();
	}

	/**
	 * Returns the real, zoom-independent height of the current page in PostScript
	 * points.
	 */
	public int getPageHeight() {
		return pdfDecoder.getPdfPageData().getMediaBoxHeight(getPage());
	}

	/**
	 * Returns the real, zoom-independent width of the current page in PostScript
	 * points.
	 */
	public int getPageWidth() {
		return pdfDecoder.getPdfPageData().getMediaBoxWidth(getPage());
	}

	/**
	 * The current zoom factor.
	 */
	private float zoom = 1;

	public float getZoom() {
		return zoom;
	}

	/**
	 * Checks whether the given zoom factor is in a sensible range.
	 */
	public boolean isZoomValid(float zoom) {
		Rectangle screenSize = Display.getDefault().getBounds();
		float newWidth = getPageWidth() * zoom;
		float newHeight = getPageHeight() * zoom;
		boolean tooBig = newWidth > screenSize.width;
		boolean tooSmall = (newWidth < 1) || (newHeight < 1);
		return !(tooBig || tooSmall);
	}

	public void setZoom(float zoom) {
		if (isZoomValid(zoom)) {
			this.zoom = zoom;
			redraw();
		}
	}

	/**
	 * The currently selected special zoom setting.
	 */
	private FitToAction fitToAction;

	public void setFitToAction(FitToAction fitToAction) {
		this.fitToAction = fitToAction;
	}

	public FitToAction getFitToAction() {
		return fitToAction;
	}

	/**
	 * Manages the contributions to the toolbar.
	 */
	private PdfViewToolbarManager toolbar;

	public void setToolbar(PdfViewToolbarManager toolbar) {
		this.toolbar = toolbar;
	}

	public PdfViewToolbarManager getToolbar() {
		return toolbar;
	}

	private void refreshToolbar() {
		if (getToolbar() != null) {
			getToolbar().refresh();
		}
	}

	/**
	 * The textedit annotations in the PDF file.
	 */
	private final List<PdfAnnotation> annotations = new ArrayList<PdfAnnotation>();

	public PdfAnnotation[] getAnnotations() {
		return annotations.toArray(new PdfAnnotation[0]);
	}

	private void loadAnnotations() {
		annotations.clear();
		AcroRenderer formRenderer = pdfDecoder.getFormRenderer();
		for (int page = 1; page <= getPageCount(); page++) {
			PdfArrayIterator pdfAnnotations = formRenderer.getAnnotsOnPage(page);
			while (pdfAnnotations.hasMoreTokens()) {
				String key = pdfAnnotations.getNextValueAsString(true);
				Object rawObject = formRenderer.getFormDataAsObject(key);
				if ((rawObject != null) && (rawObject instanceof FormObject)) {
					FormObject formObject = (FormObject)rawObject;
					int subtype = formObject.getParameterConstant(PdfDictionary.Subtype);
					if (subtype == PdfDictionary.Link) {
						PdfObject anchor = formObject.getDictionary(PdfDictionary.A);
						try {
							byte[] uriDecodedBytes = anchor.getTextStreamValue(PdfDictionary.URI).getBytes("ISO-8859-1"); //$NON-NLS-1$
							URI uri = new URI(new String(uriDecodedBytes));
							if (uri.getScheme().equals("textedit")) { //$NON-NLS-1$
								String[] sections = uri.getPath().split(":"); //$NON-NLS-1$
								String filename = (uri.getAuthority() == null ? "" : uri.getAuthority()) + sections[0]; //$NON-NLS-1$
								IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(new URI("file", filename, null)); //$NON-NLS-1$
								if (files.length > 0) {
									PdfAnnotation annotation = new PdfAnnotation();
									annotation.page = page;
									annotation.file = files[0];
									annotation.lineNumber = Integer.parseInt(sections[1]) - 1;
									annotation.columnNumber = Integer.parseInt(sections[2]); // This value is independent of tab width
									float[] rectangle = formObject.getFloatArray(PdfDictionary.Rect);
									annotation.left = rectangle[0];
									annotation.bottom = rectangle[1];
									annotation.right = rectangle[2];
									annotation.top = rectangle[3];
									annotations.add(annotation);
								}
							}
						} catch (URISyntaxException e) {
							Activator.logError("Invalid annotation URI", e);
						} catch (UnsupportedEncodingException e) {
							Activator.logError("Programming error", e);
						} catch (ArrayIndexOutOfBoundsException e) {
							Activator.logError("Error while parsing annotation URI", e);
						}
					}
				}
			}
		}
	}

	/**
	 * The composite containing the point-and-click hyperlinks.
	 */
	private Composite hyperlinks;

	/**
	 * The annotation-to-hyperlink mappings.
	 */
	private final Map<PdfAnnotation, PdfAnnotationHyperlink> annotationHyperlinkMap = new HashMap<PdfAnnotation, PdfAnnotationHyperlink>();

	/**
	 * Creates point-and-click hyperlinks from the form annotations on the current
	 * page.
	 */
	protected void createHyperlinks() {
		for (Control oldHyperlink : hyperlinks.getChildren()) {
			oldHyperlink.dispose();
		}
		annotationHyperlinkMap.clear();
		for (PdfAnnotation annotation : annotations) {
			if (annotation.page == getPage()) {
				PdfAnnotationHyperlink hyperlink = new PdfAnnotationHyperlink(hyperlinks, annotation);
				annotationHyperlinkMap.put(annotation, hyperlink);
				float zoom = getZoom();
				float left = annotation.left * zoom;
				float bottom = (getPageHeight() - annotation.bottom + 1) * zoom;
				float right = annotation.right * zoom;
				float top = (getPageHeight() - annotation.top + 1) * zoom;
				float width = right - left;
				float height = bottom - top;
				hyperlink.setBounds(new Rectangle((int)left, (int)top, (int)width, (int)height));
			}
		}
	}

	/**
	 * The currently highlighed annotation hyperlink.
	 */
	private PdfAnnotationHyperlink highlightedHyperlink;

	/**
	 * Reveals and highlights the hyperlink of the given annotation.
	 */
	public void highlightAnnotation(PdfAnnotation annotation) {
		if (highlightedHyperlink != null) {
			// TODO de-highlight
		}
		setPage(annotation.page);
		PdfAnnotationHyperlink hyperlink = annotationHyperlinkMap.get(annotation);
		if (hyperlink != null) {
			highlightedHyperlink = hyperlink;
			// TODO scroll to make it visible
			// TODO highlight
		}
	}

}
