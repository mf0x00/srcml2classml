package com.onionuml.convert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.onionuml.convert.srcml.Argument;
import com.onionuml.convert.srcml.ClassElement;
import com.onionuml.convert.srcml.Declaration;
import com.onionuml.convert.srcml.Function;
import com.onionuml.convert.srcml.IArgumentListContainer;
import com.onionuml.convert.srcml.IDeclarationContainer;
import com.onionuml.convert.srcml.INamed;
import com.onionuml.convert.srcml.IParameterListContainer;
import com.onionuml.convert.srcml.ISpecified;
import com.onionuml.convert.srcml.ITyped;
import com.onionuml.convert.srcml.Name;
import com.onionuml.convert.srcml.Parameter;
import com.onionuml.convert.srcml.RelationshipLink;
import com.onionuml.convert.srcml.RelationshipLink.LinkType;
import com.onionuml.convert.srcml.Type;
import com.onionuml.visplugin.core.RelationshipType;
import com.onionuml.visplugin.core.UmlClassElement;
import com.onionuml.visplugin.core.UmlRelationshipElement;

/**
 * Handles SAX2 events for reading a class model from a SrcML
 * XML document.
 */
public class SrcmlSaxHandler extends DefaultHandler {
	
	// PRIVATE MEMBER VARIABLES --------------------------------
	
	private String mLastPackageName = "";
	private List<String> mLastImports = new ArrayList<String>();
	
	private Map<String,List<UmlClassElement>> mPackageClassMap = new HashMap<String,List<UmlClassElement>>();
	private Map<String,UmlClassElement> mClasses = new HashMap<String,UmlClassElement>();
	
	private Map<String,String> mIdQualifiedNameMap = new HashMap<String,String>();
	private List<RelationshipLink> mAllRelationships = new ArrayList<RelationshipLink>();
	
	private int mNumClasses = 0;
	
	private Stack<String> mElementNames = new Stack<String>();
	private Stack<Object> mObjects  = new Stack<Object>();
	
	
	
	// OVERRIDE METHODS -----------------------------------
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		
		mElementNames.push(qName);
		
		
		if(qName.equals("class")){
			String stereotype = attributes.getValue("type");
			ClassElement c = new ClassElement("class" + String.valueOf(++mNumClasses),
					mLastPackageName, (stereotype != null ? stereotype : ""));
			c.setImports(mLastImports);
			mLastImports = new ArrayList<String>();
			mObjects.push(c);
		}
		else if(qName.equals("name")){
			mObjects.push(new Name());
		}
		else if(qName.equals("package") || qName.equals("import")){
			mObjects.push("");
		}
		else if(qName.equals("specifier") || qName.equals("index")){
			mObjects.push("");
		}
		else if(qName.equals("decl")){
			mObjects.push(new Declaration());
		}
		else if(qName.equals("type")){
			mObjects.push(new Type());
		}
		else if(qName.equals("argument_list")){
			mObjects.push(new ArrayList<Argument>());
		}
		else if(qName.equals("argument")){
			mObjects.push(new Argument());
		}
		else if(qName.equals("function") || qName.equals("constructor")){
			mObjects.push(new Function());
		}
		else if(qName.equals("parameter_list")){
			mObjects.push(new ArrayList<Parameter>());
		}
		else if(qName.equals("param")){
			mObjects.push(new Parameter());
		}
		else if(qName.equals("extends") || qName.equals("implements")){
			mObjects.push(new RelationshipLink());
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		
		mElementNames.pop();
		
		if(qName.equals("class")){
			ClassElement c = (ClassElement)mObjects.pop();
			UmlClassElement e = c.toUmlClassElement();
			if(e != null){
				mClasses.put(c.getId(), e);
				mPackageClassMap.get(c.getPackageName()).add(e);
				mIdQualifiedNameMap.put(c.getQualifiedName(), c.getId());
				mAllRelationships.addAll(c.makeQualifiedRelationships());
			}
		}
		else if(qName.equals("package")){
			String name = (String)mObjects.pop();
			mLastPackageName = name;
			if(!mPackageClassMap.containsKey(name)){
				mPackageClassMap.put(name, new ArrayList<UmlClassElement>());
			}
		}
		else if(qName.equals("import")){
			mLastImports.add((String)mObjects.pop());
		}
		else if(qName.equals("name")){
			Name s = (Name)mObjects.pop();
			if(parentGetsName()){
				INamed parent = (INamed)mObjects.peek();
				parent.setName(s);
			}
			else if(mElementNames.peek().equals("package") || mElementNames.peek().equals("import")){
				// if the popped name is actually part of a package name, concatenate it
				String pkgName = (String)mObjects.pop();
				if(pkgName.length() > 0){
					pkgName += ".";
				}
				pkgName += s.toString();
				mObjects.push(pkgName);
			}
		}
		else if(qName.equals("specifier")){
			String s = (String)mObjects.pop();
			if(parentGetsSpecifier()){
				ISpecified parent = (ISpecified)mObjects.peek();
				parent.addSpecifier(s);
			}
		}
		else if(qName.equals("index")){
			String s = (String)mObjects.pop();
			if(mElementNames.peek().equals("type")){
				Type t = (Type)mObjects.peek();
				t.setIndex(s);
			}
		}
		else if(qName.equals("decl")){
			Declaration d = (Declaration)mObjects.pop();
			if(parentGetsDeclaration()){
				IDeclarationContainer parent = (IDeclarationContainer)mObjects.peek();
				parent.addDeclaration(d);
			}
		}
		else if(qName.equals("type")){
			Type t = (Type)mObjects.pop();
			if(parentGetsType()){
				ITyped parent = (ITyped)mObjects.peek();
				parent.setType(t);
			}
		}
		else if(qName.equals("argument_list")){
			@SuppressWarnings("unchecked")
			List<Argument> args = (List<Argument>)mObjects.pop();
			if(parentGetsArgList()){
				IArgumentListContainer parent = (IArgumentListContainer)mObjects.peek();
				parent.addArgList(args);
			}
		}
		else if(qName.equals("argument")){
			Argument a = (Argument)mObjects.pop();
			if(mElementNames.peek().equals("argument_list")){
				@SuppressWarnings("unchecked")
				List<Argument> args = (List<Argument>)mObjects.peek();
				args.add(a);
			}
		}
		else if(qName.equals("function") || qName.equals("constructor")){
			Function f = (Function)mObjects.pop();
			if(!mObjects.isEmpty() && mObjects.peek() instanceof ClassElement && f.isPublic()){
				ClassElement e = (ClassElement)mObjects.peek();
				e.addFunction(f);
			}
		}
		else if(qName.equals("parameter_list")){
			@SuppressWarnings("unchecked")
			List<Parameter> params = (List<Parameter>)mObjects.pop();
			if(parentGetsParamList()){
				IParameterListContainer parent = (IParameterListContainer)mObjects.peek();
				parent.addParamList(params);
			}
		}
		else if(qName.equals("param")){
			Parameter p = (Parameter)mObjects.pop();
			if(mElementNames.peek().equals("parameter_list")){
				@SuppressWarnings("unchecked")
				List<Parameter> params = (List<Parameter>)mObjects.peek();
				params.add(p);
			}
		}
		else if(qName.equals("extends")){
			RelationshipLink l = (RelationshipLink)mObjects.pop();
			if(!mElementNames.isEmpty() && mElementNames.peek().equals("super")){
				ClassElement e = (ClassElement)mObjects.peek();
				l.setLinkType(LinkType.EXTENDS);
				e.addLink(l);
			}
		}
		else if(qName.equals("implements")){
			RelationshipLink l = (RelationshipLink)mObjects.pop();
			if(!mElementNames.isEmpty() && mElementNames.peek().equals("super")){
				ClassElement e = (ClassElement)mObjects.peek();
				l.setLinkType(LinkType.IMPLEMENTS);
				e.addLink(l);
			}
		}
		
	}
	
	@Override
	public void characters(char ch[], int start, int length) throws SAXException {
		
		String curElement = mElementNames.peek();
		
		if(curElement.equals("specifier") || curElement.equals("index")){
			String s = (String)mObjects.pop();
			s += new String(ch, start, length);
			mObjects.push(s);
		}
		else if(curElement.equals("name")){
			Name s = (Name)mObjects.peek();
			s.setValue(s.toString() + new String(ch, start, length));
		}
	}
	
	
	// PUBLIC METHODS -------------------------------------
	
	/**
	 * Gets a reference to the map of packages to the list of classes in each package.
	 */
	public Map<String,List<UmlClassElement>> getPackageClassMap(){
		return mPackageClassMap;
	}
	
	/**
	 * Looks up and builds the relationships among read classes.
	 */
	public List<UmlRelationshipElement> getRelationships(){
		List<UmlRelationshipElement> rels = new ArrayList<UmlRelationshipElement>();
		
		for(RelationshipLink rl : mAllRelationships){
			RelationshipType relType = null;
			switch(rl.getLinkType()){
				case IMPLEMENTS:
					relType = RelationshipType.REALIZATION;
					break;
				case AGGREGATES:
					relType = RelationshipType.AGGREGATION;
					break;
				case COMPOSES:
					relType = RelationshipType.COMPOSITION;
					break;
				case DEPENDS:
					relType = RelationshipType.DEPENDENCY;
					break;
				case EXTENDS:
					relType = RelationshipType.GENERALIZATION;
					break;
				case ASSOCIATES:
					relType = rl.isDirected() ? RelationshipType.DIRECTEDASSOCIATION
							: RelationshipType.ASSOCIATION;
					break;
			}
			if(relType != null){
				UmlRelationshipElement r = new UmlRelationshipElement("", mIdQualifiedNameMap.get(rl.getHeadName()), 
						mIdQualifiedNameMap.get(rl.getTailName()), relType);
				rels.add(r);
			}
		}
		
		return rels;
	}
	
	
	
	// PRIVATE METHODS -------------------------------------
	
	/*
	 * Returns whether the parent element should receive the current name element.
	 */
	private boolean parentGetsName(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof INamed)){
			return false;
		}
		
		String parentElement = (mElementNames.size() > 0 ? mElementNames.peek() : "");
		if(parentElement.equals("class")){
			return true;
		}
		if(parentElement.equals("decl")){
			return true;
		}
		if(parentElement.equals("name")){
			return true;
		}
		if(parentElement.equals("type")){
			return true;
		}
		if(parentElement.equals("argument")){
			return true;
		}
		if(parentElement.equals("function")){
			return true;
		}
		if(parentElement.equals("constructor")){
			return true;
		}
		if(parentElement.equals("extends")){
			return true;
		}
		if(parentElement.equals("implements")){
			return true;
		}
		
		return false;
	}
	
	/*
	 * Returns whether the parent element should receive the current specifier string.
	 */
	private boolean parentGetsSpecifier(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof ISpecified)){
			return false;
		}
		
		String parentElement = (mElementNames.size() > 0 ? mElementNames.peek() : "");
		if(parentElement.equals("class")){
			return true;
		}
		if(parentElement.equals("type")){
			return true;
		}
		if(parentElement.equals("function")){
			return true;
		}
		if(parentElement.equals("constructor")){
			return true;
		}
		
		return false;
	}
	
	/*
	 * Returns whether the parent element should receive the current type element.
	 */
	private boolean parentGetsType(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof ITyped)){
			return false;
		}
		
		String parentElement = (mElementNames.size() > 0 ? mElementNames.peek() : "");
		if(parentElement.equals("decl")){
			return true;
		}
		if(parentElement.equals("function")){
			return true;
		}
		
		return false;
	}
	
	/*
	 * Returns whether the parent element should receive the current declaration element.
	 */
	private boolean parentGetsDeclaration(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof IDeclarationContainer)){
			return false;
		}
		
		Stack<String> parents = new Stack<String>();
		boolean getsDecl = false;
		
		// if this is a declaration statement it must be a global class member to be accepted
		if(mElementNames.size() > 0 && mElementNames.peek().equals("decl_stmt")
				&& mObjects.peek() instanceof ClassElement){
			
			while(!mElementNames.isEmpty()){
				String nextParent = mElementNames.pop();
				parents.push(nextParent);
				
				if(nextParent.equals("function")){
					break;
				}
				
				if(nextParent.equals("class")){
					getsDecl = true;
					break;
				}
			}
			while(!parents.isEmpty()){
				mElementNames.push(parents.pop());
			}
		}
		else if(mElementNames.size() > 0 && mElementNames.peek().equals("param")){
			getsDecl = true;
		}
		
		return getsDecl;
	}
	
	/*
	 * Returns whether the parent element should receive the current list of arguments.
	 */
	private boolean parentGetsArgList(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof IArgumentListContainer)){
			return false;
		}
		
		String parentElement = (mElementNames.size() > 0 ? mElementNames.peek() : "");
		if(parentElement.equals("name")){
			return true;
		}
		
		return false;
	}
	
	/*
	 * Returns whether the parent element should receive the current list of parameters.
	 */
	private boolean parentGetsParamList(){
		if(mObjects.size() == 0 || !(mObjects.peek() instanceof IParameterListContainer)){
			return false;
		}
		
		String parentElement = (mElementNames.size() > 0 ? mElementNames.peek() : "");
		if(parentElement.equals("function")){
			return true;
		}
		if(parentElement.equals("constructor")){
			return true;
		}
		
		return false;
	}
}
