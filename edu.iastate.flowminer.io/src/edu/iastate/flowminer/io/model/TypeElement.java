package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

public abstract class TypeElement extends Element {
	public TypeElement(IProgressMonitor mon, VTDNav vn) throws NavException, ParseException {
		super(mon, vn);
	}

	public TypeElement(long id, String name) {
		super(id, name);
	}

	public TypeElement(String name) {
		super(name);
	}
}
