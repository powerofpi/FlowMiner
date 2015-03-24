package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class AnnotationTypeElement extends NonPrimitiveTypeElement {
	public AnnotationTypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public AnnotationTypeElement(long id, String name) {
		super(id, name);
	}

	public AnnotationTypeElement(String name) {
		super(name);
	}
}
