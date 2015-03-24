package edu.iastate.flowminer.io;


public interface Schema {
	public static final int RADIX = 36;
	
	public static final String ATTR_KEY = "a";
	public static final String ATTR_VAL = "b";
	public static final String ELEMENT_NAME = "c";
	public static final String ELEMENT_ID = "d";
	public static final String ELEMENT_ATTR = "e";
	public static final String ELEMENT_TAG = "f";
	public static final String IOMODEL = "g";
	public static final String IOMODEL_LIBRARY = "h";
	public static final String IOMODEL_RELATIONSHIP = "i";
	public static final String IOMODEL_PRIMITIVE = "j";
	public static final String IOMODEL_AUTHOR = "k";
	public static final String IOMODEL_CREATED = "l";
	public static final String IOMODEL_NUM_ELEMENTS = "m";
	public static final String RELATIONSHIP_ORIGIN_ID = "n";
	public static final String RELATIONSHIP_DEST_ID = "o";
	public static final String RELATIONSHIP_SCHEMA_TYPE = "p";
	public static final String PACKAGE_CLASS = "q";
	public static final String PACKAGE_INTERFACE = "r";
	public static final String PACKAGE_ENUM = "s";
	public static final String PACKAGE_ANNOTATION = "t";
	public static final String TYPE_NON_PRIMITIVE_FIELD = "u";
	public static final String TYPE_NON_PRIMITIVE_METHOD = "v";
	public static final String TYPE_NON_PRIMITIVE_NESTED_CLASS = "w";
	public static final String TYPE_NON_PRIMITIVE_NESTED_INTERFACE = "x";
	public static final String TYPE_NON_PRIMITIVE_NESTED_ENUM = "y";
	public static final String TYPE_NON_PRIMITIVE_NESTED_ANNOTATION = "z";
	public static final String TYPE_NON_PRIMITIVE_CONSTRUCTOR = "aa";
	public static final String TYPE_NON_PRIMITIVE_EXTENDS = "ab";
	public static final String TYPE_NON_PRIMITIVE_IMPLEMENTS = "ac";
	public static final String ENUM_TYPE_NON_PRIMITIVE_CONSTANT = "ad";
	public static final String METHOD_PARAM = "ae";
	public static final String METHOD_RETURNED = "af";
	public static final String METHOD_OVERRIDES = "ag";
	public static final String METHOD_LOCAL_CLASS = "ah";
	public static final String METHOD_LOCAL_INTERFACE = "ai";
	public static final String METHOD_LOCAL_ENUM = "aj";
	public static final String METHOD_LOCAL_ANNOTATION = "ak";
	public static final String METHOD_LOCAL_VAR = "al";
	public static final String METHOD_CONTEXT_THIS = "am";
	public static final String VAR_ARRAY_DIM = "an";
	public static final String VAR_TYPE = "ao";
	public static final String PARAM_VAR_INDEX = "ap";
	public static final String LOCAL_VAR_SCHEMA_TYPE = "aq";
	public static final String LIBRARY_PACKAGE = "ar";
	
	public static interface Tag{
		public static final String IS_PUBLIC = "as";
		public static final String IS_PROTECTED = "at";
		public static final String IS_PRIVATE = "au";
		public static final String IS_ABSTRACT = "av";
		public static final String IS_FINAL = "aw";
		public static final String IS_NATIVE = "ax";
		public static final String IS_STATIC = "ay";
		public static final String IS_SYNCHRONIZED = "az";
		public static final String IS_STRICTFP = "ba";
		public static final String IS_TRANSIENT = "bb";
		public static final String IS_VOLATILE = "bc";
	}
}
