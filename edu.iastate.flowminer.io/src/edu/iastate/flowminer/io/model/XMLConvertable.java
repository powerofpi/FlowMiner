package edu.iastate.flowminer.io.model;

import java.text.ParseException;

import net.ontopia.utils.CompactHashMap;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ximpleware.NavException;
import com.ximpleware.VTDNav;

import edu.iastate.flowminer.io.common.XMLUtils;
import edu.iastate.flowminer.io.log.Log;

public abstract class XMLConvertable {
	public static final int NOT_DEFINED = -1;
	private IProgressMonitor mon;
	private VTDNav vn;
	public XMLConvertable(IProgressMonitor mon, VTDNav vn){
		this.mon = mon;
		this.vn = vn;
	}

	public final void parse() throws NavException, ParseException{
		if(vn.toElement(VTDNav.FC)){
			do{
				if(mon.isCanceled()) return;
				interpretChild(mon, vn, vn.toString(vn.getCurrentIndex()));
			}while(vn.toElement(VTDNav.NS));
			vn.toElement(VTDNav.P);
		}
		
		if(mon != null) mon.worked(1);
	}
	
	public abstract boolean interpretChild(IProgressMonitor mon, VTDNav vn, String name) throws NavException, ParseException;
	
	public XMLConvertable() {
	}

	public static String getTypeName(XMLConvertable toGet) {
		return getTypeName(toGet.getClass());
	}

	public static String getTypeName(Class<? extends XMLConvertable> toGet) {
		return toGet.getName();
	}

	private static CompactHashMap<String,String> stringToEscaped = new CompactHashMap<String,String>();
	
	private static String escape(String s){
		String escaped = stringToEscaped.get(s);
		if(escaped == null){
			escaped = XMLUtils.writeValidXMLText(s);
			stringToEscaped.put(s, escaped);
		}
		return escaped;
	}
			
	protected static void createNode(StringBuilder sb, String name, String content) {
		String escapedName = escape(name);
		sb.append("<").append(escapedName).append(">").
		   append(escape(content)).
		   append("</").append(escapedName).append(">");
	}
	
	public static void startNode(StringBuilder sb, String name){
		sb.append("<").append(escape(name)).append(">");
	}
	
	public static void rootNode(StringBuilder sb, String name){
		sb.append("<").append(escape(name)).append(" ").
		append("xmlns=\"http://" + Log.pluginid + "\" ").
		append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ").
		append("xsi:schemaLocation=\"http://" + Log.pluginid + " " + Log.pluginid + ".xsd\"").append(">");
	}
	
	public static void endNode(StringBuilder sb, String name){
		sb.append("</").append(escape(name)).append(">");
	}
	
	public abstract void convert(StringBuilder sb, String name);
	
	public abstract void releaseMemory();
	
	/**
	 * Return the number of elements in the model declared underneath this element.
	 * @return
	 */
	public abstract int subtreeSize();
}
