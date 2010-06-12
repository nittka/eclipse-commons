package org.eclipse.ui.views.file;

/**
 * Defines the file to be shown in a file view.
 */
public interface FileViewSource {

	/**
	 * Initializes this source. Called when this source is selected as the source
	 * of the given file view.
	 */
	void init(FileView fileView);

	/**
	 * Shuts down this source. Called when this source ceases to be the source of
	 * a file view.
	 */
	void done();

	/**
	 * Returns the user-friendly name of the file source to be displayed on the
	 * UI.
	 */
	String getName();

}
