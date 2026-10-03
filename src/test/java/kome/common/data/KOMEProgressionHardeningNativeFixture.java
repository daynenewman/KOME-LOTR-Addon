package kome.common.data;
import java.util.*;import java.util.jar.*;import java.lang.reflect.*;
import lotr.common.fac.*;import lotr.common.entity.npc.*;import lotr.common.world.spawning.*;
/** Boots only native relations/resources used by the matrix; all mutable native globals are restored. */
final class KOMEProgressionHardeningNativeFixture implements AutoCloseable {
 final Map defaultRelations,oldRelations,overrides,oldOverrides; final Field namesField;final Object oldNames;
 final Map<LOTRInvasions,List> invasions=new EnumMap<>(LOTRInvasions.class);
 final Map<LOTRFaction,List<String>> aliases=new EnumMap<>(LOTRFaction.class);
 final List<Class<?>> captains=new ArrayList<>();
 KOMEProgressionHardeningNativeFixture()throws Exception {
  Field r=LOTRFactionRelations.class.getDeclaredField("defaultMap");r.setAccessible(true);defaultRelations=(Map)r.get(null);oldRelations=new HashMap(defaultRelations);
  r=LOTRFactionRelations.class.getDeclaredField("overrideMap");r.setAccessible(true);overrides=(Map)r.get(null);oldOverrides=new HashMap(overrides);overrides.clear();
  namesField=LOTRNames.class.getDeclaredField("allNameBanks");namesField.setAccessible(true);oldNames=namesField.get(null);
  for(LOTRInvasions v:LOTRInvasions.values())invasions.put(v,v.invasionMobs);
  for(LOTRFaction faction:LOTRFaction.values())aliases.put(faction,faction.listAliases());
  nativeRelations();nativeAliases();LOTRInvasions.createMobLists();
  Field names=LOTRNames.class.getDeclaredField("allNameBanks");names.setAccessible(true);Map<String,String[]> banks=new HashMap<>();
   try(JarFile namesJar=new JarFile(new java.io.File(LOTRFaction.class.getProtectionDomain().getCodeSource().getLocation().toURI()))){Enumeration<JarEntry> en=namesJar.entries();while(en.hasMoreElements()){JarEntry e=en.nextElement();if(e.getName().startsWith("assets/lotr/names/")&&e.getName().endsWith(".txt")){List<String> values=new ArrayList<>();try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(namesJar.getInputStream(e),"UTF-8"))){String line;while((line=reader.readLine())!=null){line=line.trim();if(!line.isEmpty()&&!line.startsWith("#"))values.add(line);}}banks.put(e.getName().substring("assets/lotr/names/".length(),e.getName().length()-4),values.toArray(new String[0]));}}}names.set(null,banks);

  try(JarFile jar=new JarFile(new java.io.File(LOTRFaction.class.getProtectionDomain().getCodeSource().getLocation().toURI()))){
    Enumeration<JarEntry> entries=jar.entries();while(entries.hasMoreElements()){String n=entries.nextElement().getName();if(n.startsWith("lotr/common/entity/npc/LOTREntity")&&n.endsWith(".class")&&!n.contains("$")){
     Class<?> c=Class.forName(n.substring(0,n.length()-6).replace('/','.'),false,LOTRFaction.class.getClassLoader());
     if(LOTREntityNPC.class.isAssignableFrom(c)&&LOTRUnitTradeable.class.isAssignableFrom(c)&&!Modifier.isAbstract(c.getModifiers()))captains.add(c);
    }}
  }
 }
 public void close()throws Exception {defaultRelations.clear();defaultRelations.putAll(oldRelations);overrides.clear();overrides.putAll(oldOverrides);namesField.set(null,oldNames);for(Map.Entry<LOTRInvasions,List> e:invasions.entrySet())e.getKey().invasionMobs=e.getValue();Field field=LOTRFaction.class.getDeclaredField("legacyAliases");field.setAccessible(true);for(Map.Entry<LOTRFaction,List<String>> e:aliases.entrySet()){List values=(List)field.get(e.getKey());values.clear();values.addAll(e.getValue());}}
 static void nativeAliases()throws Exception {
  org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
  try(java.io.InputStream in=LOTRFaction.class.getResourceAsStream("LOTRFaction.class")){new org.objectweb.asm.ClassReader(in).accept(node,0);}
  Method add=LOTRFaction.class.getDeclaredMethod("addLegacyAlias",String.class);add.setAccessible(true);int count=0;
  for(Object value:node.methods){org.objectweb.asm.tree.MethodNode method=(org.objectweb.asm.tree.MethodNode)value;if(!method.name.equals("initAllProperties"))continue;
   for(org.objectweb.asm.tree.AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof org.objectweb.asm.tree.MethodInsnNode&&((org.objectweb.asm.tree.MethodInsnNode)i).name.equals("addLegacyAlias")){
    String alias=(String)((org.objectweb.asm.tree.LdcInsnNode)i.getPrevious()).cst;
    String faction=((org.objectweb.asm.tree.FieldInsnNode)i.getPrevious().getPrevious()).name;add.invoke(LOTRFaction.class.getField(faction).get(null),alias);count++;
   }
  }if(count!=6)throw new AssertionError("Review native alias definitions: "+count);
 }
  static void nativeRelations() throws Exception {
  org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
  try(java.io.InputStream in=LOTRFaction.class.getResourceAsStream("LOTRFaction.class")){new org.objectweb.asm.ClassReader(in).accept(node,0);}
  org.objectweb.asm.tree.MethodNode source=null;for(Object value:node.methods){org.objectweb.asm.tree.MethodNode m=(org.objectweb.asm.tree.MethodNode)value;if(m.name.equals("initAllProperties"))source=m;}
  org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
  writer.visit(52,org.objectweb.asm.Opcodes.ACC_PUBLIC,"kome/test/NativeRelationsBoot",null,"java/lang/Object",null);
  org.objectweb.asm.MethodVisitor out=writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC|org.objectweb.asm.Opcodes.ACC_STATIC,"init","()V",null,null);out.visitCode();
  org.objectweb.asm.tree.AbstractInsnNode end=null;for(org.objectweb.asm.tree.AbstractInsnNode i=source.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof org.objectweb.asm.tree.FieldInsnNode&&i.getOpcode()==org.objectweb.asm.Opcodes.PUTFIELD){end=i.getPrevious().getPrevious();break;}
  if(end==null||!((org.objectweb.asm.tree.FieldInsnNode)end.getNext().getNext()).name.equals("approvesWarCrimes"))throw new AssertionError("Native relation boot boundary missing");
  for(org.objectweb.asm.tree.AbstractInsnNode i=source.instructions.getFirst();i!=end;i=i.getNext())i.accept(out);
  out.visitInsn(org.objectweb.asm.Opcodes.RETURN);out.visitMaxs(0,0);out.visitEnd();writer.visitEnd();final byte[] bytes=writer.toByteArray();
  Class<?> boot=new ClassLoader(LOTRFaction.class.getClassLoader()){Class<?> define(){return defineClass("kome.test.NativeRelationsBoot",bytes,0,bytes.length);}}.define();boot.getMethod("init").invoke(null);
 }

}
