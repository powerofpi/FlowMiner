package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public class InterfaceTypeElement extends NonPrimitiveTypeElement {
	public InterfaceTypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
		parse();
	}

	public InterfaceTypeElement(long id, String name) {
		super(id, name);
	}

	public InterfaceTypeElement(String name) {
		super(name);
	}
}
