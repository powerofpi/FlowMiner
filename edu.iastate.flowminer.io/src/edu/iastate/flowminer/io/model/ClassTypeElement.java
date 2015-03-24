package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class ClassTypeElement extends NonPrimitiveTypeElement {
	public ClassTypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public ClassTypeElement(long id, String name) {
		super(id, name);
	}

	public ClassTypeElement(String name) {
		super(name);
	}
}
