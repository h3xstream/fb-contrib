/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for definitions of methods that have an array as the last parameter. Since this class is compiled with java 1.5 or better, it would be more flexible
 * for clients of this method to define this parameter as a vararg parameter.
 */
public class UseVarArgs extends PreorderVisitor implements Detector {
    private final BugReporter bugReporter;
    private JavaClass javaClass;

    public UseVarArgs(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to make sure that the class was compiled by java 1.5 or later.
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            javaClass = classContext.getJavaClass();
            if (javaClass.getMajor() >= Constants.MAJOR_1_5) {
                javaClass.accept(this);
            }
        } finally {
            javaClass = null;
        }
    }

    /**
     * overrides the visitor to look for methods that has an array as a last parameter of an array type, where the base type is not like the previous parameter
     * nor something like a char or byte array.
     *
     * @param obj
     *            the currently parse method
     */
    @Override
    public void visitMethod(Method obj) {
        try {
            if (obj.isSynthetic()) {
                return;
            }

            Type[] types = obj.getArgumentTypes();
            if ((types.length == 0) || (types.length > 2)) {
                return;
            }

            if ((obj.getAccessFlags() & Constants.ACC_VARARGS) != 0) {
                return;
            }

            String lastParmSig = types[types.length - 1].getSignature();
            if (!lastParmSig.startsWith("[") || lastParmSig.startsWith("[[")) {
                return;
            }

            if ("[B".equals(lastParmSig) || "[C".equals(lastParmSig)) {
                return;
            }

            if (hasSimilarParms(types)) {
                return;
            }

            if (obj.isStatic() && "main".equals(obj.getName()) && "([Ljava/lang/String;)V".equals(obj.getSignature())) {
                return;
            }

            if (!obj.isPrivate() && !obj.isStatic() && isInherited(obj)) {
                return;
            }

            super.visitMethod(obj);
            bugReporter.reportBug(new BugInstance(this, BugType.UVA_USE_VAR_ARGS.name(), LOW_PRIORITY).addClass(this).addMethod(this));

        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * overrides the visitor, but not used
     */
    @Override
    public void report() {
        // needed by Detector interface but not used
    }

    /**
     * determines whether a bunch of types are similar and thus would be confusing to have one be a varargs.
     *
     * @param argTypes
     *            the parameter types to check
     * @return whether the parameter are similar
     */
    private static boolean hasSimilarParms(Type... argTypes) {

        for (int i = 0; i < (argTypes.length - 1); i++) {
            if (argTypes[i].getSignature().startsWith("[")) {
                return true;
            }
        }

        String baseType = argTypes[argTypes.length - 1].getSignature();
        while (baseType.startsWith("[")) {
            baseType = baseType.substring(1);
        }

        for (int i = 0; i < (argTypes.length - 1); i++) {
            if (argTypes[i].getSignature().equals(baseType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * looks to see if this method is derived from a super class. If it is we don't want to report on it, as that would entail changing a whole hierarchy
     *
     * @param m
     *            the current method
     * @return if the method is inherited
     *
     * @throws ClassNotFoundException
     *             if the super class(s) aren't found
     */
    private boolean isInherited(Method m) throws ClassNotFoundException {
        JavaClass[] infs = javaClass.getAllInterfaces();
        for (JavaClass inf : infs) {
            if (hasMethod(inf, m)) {
                return true;
            }
        }

        JavaClass[] sups = javaClass.getSuperClasses();
        for (JavaClass sup : sups) {
            if (hasMethod(sup, m)) {
                return true;
            }
        }

        return false;
    }

    /**
     * looks to see if a class has a method with a specific name and signature
     *
     * @param c
     *            the class to check
     * @param candidateMethod
     *            the method to look for
     *
     * @return whether this class has the exact method
     */
    private static boolean hasMethod(JavaClass c, Method candidateMethod) {
        String name = candidateMethod.getName();
        String sig = candidateMethod.getSignature();

        for (Method method : c.getMethods()) {
            if (!method.isStatic() && method.getName().equals(name) && method.getSignature().equals(sig)) {
                return true;
            }
        }

        return false;
    }
}
