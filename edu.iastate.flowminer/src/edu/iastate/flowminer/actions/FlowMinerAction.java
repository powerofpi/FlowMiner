package edu.iastate.flowminer.actions;

import java.io.File;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

public abstract class FlowMinerAction implements IWorkbenchWindowActionDelegate{
	IWorkbenchWindow window;
	
	String[] chooseJARs(String message){
	    FileDialog dialog = new FileDialog(window.getShell(), SWT.MULTI);
	    dialog.setText(message);
	    dialog.setFilterNames(new String[] { "Java Library (*.jar)", "All Files (*.*)" });
	    dialog.setFilterExtensions(new String[] { "*.jar", "*.*" });
	    dialog.open();
	    
	    // Make paths absolute
	    String[] fileNames = dialog.getFileNames();
	    String filterPath = dialog.getFilterPath();
	    for(int i = 0; i < fileNames.length; ++i){
	    	fileNames[i] = filterPath + File.separator + fileNames[i];
	    }
	    
	    return fileNames;
	}
	
	String chooseFile(String message, String typeDescription, String typeExtension){
	    FileDialog dialog = new FileDialog(window.getShell(), SWT.SAVE);
	    dialog.setText(message);
	    dialog.setFilterNames(new String[] { typeDescription, "All Files (*.*)" });
	    dialog.setFilterExtensions(new String[] { "*"+typeExtension, "*.*" });
	    
	    String savePath = dialog.open();
	    if(savePath != null){
	    	if(savePath.endsWith(typeExtension)) return savePath;
	    	return savePath + typeExtension;
	    }
	    return null;
	}
	
	String chooseSummaryFile(String message){
		return chooseFile(message, "FlowMiner Summaries", ".xml.gz");
	}
	
	public void selectionChanged(IAction action, ISelection selection) {}
	public void dispose() {}
	public void init(IWorkbenchWindow window) {
		this.window = window;
	}
}
