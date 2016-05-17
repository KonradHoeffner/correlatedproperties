import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

/** Use alldifferent.tsv created by FromLemon and add correlated resource labels to it. Because only a small amount of them are properties (which already exist),
 * we also need to get correlated classes.
 * If it's a property if uses the properties from output/correlatedproperties/combined/correlatedproperties.tsv. If it's a class it uses the SPARQL query:
<pre>select distinct(?c), ?l, count(?s as ?cnt) from <http://dbpedia.org> {?s a <"+clazz+">. ?s a ?c.
FILTER(!STRSTARTS(STR(?c), \"http://dbpedia.org/class/yago/\")).
 ?c rdfs:label ?l. FILTER(langmatches(lang(?l),\"en\")).} order by desc(count(?s))";</pre>
And from the results I remove all superclasses (recursively, that is transitively) from it (because superclasses would else always be in the results in DBpedia because it lists all classes for a class including superclasses).**/
public class LemonDifferentCorrelated
{
	static final File CORRELATED_PROPERTIES_FILE = new File("output/correlatedproperties/combined/correlatedproperties.tsv");

//	static final File OUTPUT_FOLDER = new File("output/correlatedlemon");
//	static final File OUTPUT_FILE = Paths.get(OUTPUT_FOLDER.getPath(),"correlatedlemon.tsv").toFile();
//	static final File INPUT_FILE = Lemon.allDifferent;
	static final File OUTPUT_FOLDER = new File("output/correlatedqald");
	static final File OUTPUT_FILE = Paths.get(OUTPUT_FOLDER.getPath(),"correlatedqald.tsv").toFile();
	static final File INPUT_FILE = new File("input/qaldonlylabels.tsv");
	
	public static Set<String> resultSetToList(ResultSet rs)
	{
		Set<String> list = new HashSet<String>();
		while(rs.hasNext())
		{
			QuerySolution qs = rs.nextSolution();
			try {
				list.add(URLDecoder.decode(qs.get(qs.varNames().next()).toString(), "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				list.add(qs.get(qs.varNames().next()).toString());
				e.printStackTrace();
			}
		}
		return list;
	}
	public static Set<String> superClassesOf(String clazz) {return superClassesOf(new HashSet<String>(Collections.singletonList(clazz)));}
	public static Set<String> superClassesOf(Set<String> classes)
	{
		Set<String> superClasses = new HashSet<String>();    	
		for(String clazz: classes)
		{
			String query = "SELECT distinct(?class) from <"+Defaults.graph+"> {<"+clazz+"> rdfs:subClassOf ?class.}";
			superClasses.addAll(resultSetToList(new QueryEngineHTTP(Defaults.endpoint, query).execSelect()));
		}      
		if(!superClasses.contains("http://www.w3.org/2002/07/owl#Thing")&&superClasses.size()>classes.size())
		{
			superClasses.addAll(superClassesOf(superClasses));
		}
		return superClasses;
	}

	static String labelFreqSummarize(String label,int frequency)
	{
		return label+"="+frequency;
	}
	
//	public static Map<String,Integer> getCorrelatedClassLabels(String clazz)
	public static List<String> getCorrelatedClassLabels(String clazz)
	{
		List<String> x = new LinkedList<>();
//		if(!clazz.startsWith("dbo:")) clazz=clazz.replace("dbo:","http://dbpedia.org/ontology/");
//		else if(!clazz.startsWith("dbp:")) clazz=clazz.replace("dbp:","http://dbpedia.org/property/");else
		if(!clazz.startsWith("http://")) clazz=Defaults.DBO+clazz;
		Set<String> superClasses = superClassesOf(clazz);
//		Map<String,Integer> clazzes = new HashMap<String,Integer>();
		// no yago, because it's class hierarchy is too flat, we would get too many useless low frequency results 
		String query = "select distinct(?c), ?l, count(?s) as ?cnt {?s a <"+clazz+">. ?s a ?c.  FILTER(!STRSTARTS(STR(?c), \"http://dbpedia.org/class/yago/\")). ?c rdfs:label ?l. FILTER(langmatches(lang(?l),\"en\")).} order by desc(count(?s))";
//		System.out.println(query);
		ResultSet rs = new QueryEngineHTTP(Defaults.endpoint, query).execSelect();
		int classFrequency = 0;
		String classLabel = null;
		boolean empty = true;
		while(rs.hasNext())
		{
			empty=false;
			QuerySolution qs = rs.next();
			String correlatedClazz = qs.get("?c").asResource().getURI();
			String correlatedClassLabel = qs.get("?l").asLiteral().getLexicalForm();
			if(superClasses.contains(correlatedClazz)) {continue;}
//			for(Iterator<String> it = qs.varNames();it.hasNext();) System.out.println(it.next());
			int frequency = qs.get("?cnt").asLiteral().getInt();
			// todo: label from the resource itself should come first
			if(clazz.equals(correlatedClazz))
			{
				classFrequency=frequency; // max occurrences with itself
				classLabel = correlatedClassLabel;
				continue;
			}
			x.add(labelFreqSummarize(correlatedClassLabel, frequency));
//			clazzes.put(correlatedClassLabel, frequency);
		}
		// ensure that the classes label and frequency is always in the first position
		if(classLabel!=null)
		{
			x.add(0, labelFreqSummarize(classLabel, classFrequency));
		} else if(!empty) throw new RuntimeException(clazz+": "+query);
		return x;
//		return clazzes;
	}

	public static void main(String[] args) throws FileNotFoundException
	{
		Map<String,Map<String,Integer>> correlatedPropertyLabelFrequencies = new HashMap<>();
		Map<String,String> propertyLabels = new HashMap<>();

		try(Scanner in = new Scanner(CORRELATED_PROPERTIES_FILE))
		{
			while(in.hasNextLine())
			{
				String line = in.nextLine();
				if(line.isEmpty()) {continue;}
				String[] tokens = line.split("\t");
				String property = tokens[0];
				String propertyLabel = tokens[1];
				propertyLabels.put(property,propertyLabel);
				int frequency = Integer.valueOf(tokens[4]); 
				String correlatedLabel = tokens[3];
				Map<String,Integer> labelFrequency = correlatedPropertyLabelFrequencies.get(property);
				if(labelFrequency==null) {correlatedPropertyLabelFrequencies.put(property,labelFrequency = new HashMap<String,Integer>());}
				labelFrequency.put(correlatedLabel, frequency);
			}
		}
//		System.out.println(correlatedPropertyLabelFrequencies);
		//		Map<String,Map<String,Integer>> correlatedClassFrequencies = new HashMap<>();

		OUTPUT_FOLDER.mkdirs();
		
		try(PrintWriter out = new PrintWriter(OUTPUT_FILE))
		{
		try(Scanner in = new Scanner(INPUT_FILE))
		{
			int i=0;
			while(in.hasNextLine())
			{				
				String line = in.nextLine();
				if(line.isEmpty()) {continue;}
//				System.out.println(line);
				String[] tokens = line.split("\t");
				String keyword = tokens[0].trim();
				String resource = tokens[1].trim().replace("dbo:", "");
//				System.out.println(resource);
//				Map<String,Integer> labelFrequencies;
				// ugly code ahead. improve it should be used in another context.
				Object labelFrequenciesObject=null;
				if(Character.isLowerCase(resource.charAt(0)))
				{
					// it's a property
					Map<String,Integer> labelFrequencies = correlatedPropertyLabelFrequencies.get(resource);
					if(labelFrequencies!=null)
					{
					String s = labelFrequencies.toString();
					if(!labelFrequencies.containsKey(resource)) {s=propertyLabels.get(resource)+"=?, "+s;}
					labelFrequenciesObject = s;
					}
				}
				else if(Character.isUpperCase(resource.charAt(0)))
				{
					labelFrequenciesObject = getCorrelatedClassLabels(resource);				
				}
				else {throw new AssertionError(resource);}
				if(labelFrequenciesObject==null||labelFrequenciesObject.toString().length()<3) {System.err.println("No result for "+resource+", skipping.");continue;}
				String s = CorrelatedProperties.tsv(keyword,resource,labelFrequenciesObject.toString().replaceAll("[\\[\\]\\{\\}]", ""));
				System.out.println(++i+": "+s);
				out.println(s);
			}
		}
		}
	}

}