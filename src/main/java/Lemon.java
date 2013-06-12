import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

/** extract property pairs from the lemon files on github **/
public class Lemon
{		
	public static File folderCombined = new File("output/lemon/combined");
	public static File allDifferent = Paths.get(folderCombined.getPath(),"alldifferent.tsv").toFile();
	
	public static void main(String[] args) throws IOException
	{
		File folderSingle = new File("output/lemon/single");
		folderSingle.mkdirs();
				
		folderCombined.mkdirs();

		String baseURL = "https://raw.github.com/cunger/lemon.dbpedia/master/en/";
		String[] fileNames = {"animals_plants.ldp","arts_entertainment.ldp","body_health.ldp","buildings.ldp","misc.ldp","persons_organizations.ldp","places.ldp","space.ldp","sports.ldp","technology_transportation.ldp"};	
		
		File allSimilar = new File(folderSingle+"/allsimilar.tsv");

		for(File f: new File[] {allSimilar,allDifferent})
		{
			if(f.exists()) {System.err.println(f+" already exists, exiting.");System.exit(1);}
		}
		try	(PrintWriter outAllSimilar = new PrintWriter(allSimilar);PrintWriter outAllDifferent = new PrintWriter(allDifferent))
		{			
			for(String fileName : fileNames)
			{
				try(PrintWriter outSimilar = new PrintWriter(folderSingle+"/"+fileName+"_similar.tsv");PrintWriter outDifferent= new PrintWriter(folderSingle+"/"+fileName+"different.tsv"))
				{				
					URL url = new URL(baseURL+fileName);
					Pattern pSingle = Pattern.compile("\\(\"(\\w+)\",dbpedia:(\\w+)\\)");
					Pattern pMulti = Pattern.compile("\\(\\[([^\\]]+)\\],dbpedia:(\\w+)\\)");
					Pattern pSingleFromMulti = Pattern.compile("\"(\\w+)\"");
					try(Scanner in = new Scanner(url.openStream(), "UTF-8"))
					{				
						while(in.hasNextLine())
						{
							String line = in.nextLine();
							String label = "";							
							Matcher m1 = pSingle.matcher(line);
							String id = null;							
							if(m1.find())
							{
								m1.group(1);
								id = m1.group(2);
								label = m1.group(1);
//								System.out.println(label+"---"+id);
							} else
							{
								Matcher m2 = pMulti.matcher(line);

								if(m2.find())
								{									
									Matcher m3 = pSingleFromMulti.matcher(m2.group(1));
									System.out.println(m2.group(1));
									while(m3.find())
									{										
//											System.out.println(m3.group(1));
											label+=m3.group(1)+" ";
									}
									label = label.substring(0,label.length()-1);
									id = m2.group(2);
//									System.out.println(label+"---"+id);
								} else {continue;}
							}
							String dbpediaResource = "http://dbpedia.org/ontology/"+id;;
													
							String dbpediaLabel = null;
							{
								String query = "select ?l from <http://dbpedia.org> {<"+dbpediaResource+"> rdfs:label ?l. filter(langmatches(lang(?l),\"en\"))} limit 1";
								ResultSet rs = new QueryEngineHTTP("http://live.dbpedia.org/sparql", query).execSelect();								
								if(!rs.hasNext()) {continue;}
								//									{
								RDFNode node = rs.next().get("?l");								
								dbpediaLabel = node.asLiteral().getLexicalForm();
								// save effort by removing the allways-the-same dbo prefix
								dbpediaResource.replace(Defaults.DBO,"");
								@SuppressWarnings("resource") PrintWriter out = levenshtein(label, dbpediaLabel)>0.7?outSimilar:outDifferent;											
								out.println(CorrelatedProperties.tsv(label, dbpediaResource,dbpediaLabel));

								@SuppressWarnings("resource") PrintWriter allOut = levenshtein(label, dbpediaLabel)>0.7?outAllSimilar:outAllDifferent	;											
								allOut.println(CorrelatedProperties.tsv(label, dbpediaResource,dbpediaLabel));
							}

						}
					}
				
				
			}
		}
	}
}

/** Copied somewhere from the internet and modified for double values and 1 being most similar**/
static public double levenshtein(String a, String b)
{
	/* Schritt 1 */
	int n = a.length();
	int m = b.length();
	int cost,minimum;
	char chOriginal,chInput;

	if(n == 0 || m == 0)
		return 0;

	int hoehe = n + 1;
	int breite = m + 1;

	int[][] matrix = new int[hoehe][breite];

	/* Schritt 2 */
	for(int i = 0;i < hoehe;i++)
		matrix[i][0] = i;

	for(int j = 0;j < breite;j++)
		matrix[0][j] = j;

	/* Schritte 3 - 6 */
	for(int i = 1;i < hoehe;i++) {
		chOriginal = a.charAt(i-1);

		for(int j = 1;j < breite;j++) {
			chInput = b.charAt(j-1);

			/* Wenn Zeichen übereinstimmen -> Keine Substitution etc. notwendig -> 0 Kosten */
			if(chOriginal == chInput)
				cost = 0;
			else
				cost = 1;

			/* Berechnung des Minimums */	
			minimum = matrix[i-1][j]+1;

			if((matrix[i][j-1]+1) < minimum)
				minimum = matrix[i][j-1]+1;

			if((matrix[i-1][j-1] + cost) < minimum)
				minimum = matrix[i-1][j-1] + cost;

			matrix[i][j] = minimum;
		}
	}			
	return 1-((double)matrix[n][m])/Math.max(a.length(), b.length());	
}
}

//Set<String> resources = new HashSet<>();
//{
//	String query = "select ?r from <http://dbpedia.org> {?r rdfs:label \""+label+"\"@en.FILTER(STRSTARTS(STR(?r), \"http://dbpedia.org/ontology/\")). }";
//	//											System.out.println(query);
//	ResultSet rs = new QueryEngineHTTP("http://live.dbpedia.org/sparql", query).execSelect();								
//	while(rs.hasNext())
//	{
//		Resource resource = rs.next().get("?r").asResource();								
//		resources.add(resource.getURI());
//		//										System.out.println(label+"-------->"+resource);
//		if(!resource.getURI().equals(dbpediaResource))
//		{
//			PrintWriter out = levenshtein(label, dbpediaLabel)>0.7?outSimilar:outDifferent;											
//			out.println(Main.tsv(resource.getURI(),label, dbpediaResource,dbpediaLabel));
//
//			PrintWriter allOut = levenshtein(label, dbpediaLabel)>0.7?outAllSimilar:outAllDifferent	;											
//			allOut.println(Main.tsv(resource.getURI(),label, dbpediaResource,dbpediaLabel));
//		}
//	}
//}