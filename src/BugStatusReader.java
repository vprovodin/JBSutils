import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * Created by vprovodin on 21/12/2016.
 */
public class BugStatusReader {

    static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_1)";
    static FileSystem defaultFileSystem = FileSystems.getDefault();
    static TextFileWriter writerFixedIssues;
    static TextFileWriter writerTestIssues;
    static int countFixed = 0;
    static int countTestIssues = 0;

    private static void printUsage() {
        String usage =
                "\njava BugStatusReader [options]      " +
                        "                                      \n\n" +
                        "where options include:                " +
                        "                                      \n" +
                        "    -r | -results <result directory>  " +
                        "directory to which reports are stored \n" +
                        "    -pl <path to the problem list>     ";
        System.out.println(usage);
        System.exit(0);
    }

    /**
     * main
     */
    public static void main(String args[]) {

        String resDir = "out";
        String problemList = null;
        try {

            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-results") ||
                        args[i].startsWith("-r")) {
                    i++;
                    resDir = args[i];
                } else if (args[i].startsWith("-pl")) {
                    i++;
                    problemList = args[i];
                }
            }
        } catch (Exception e) {
            printUsage();
        }

        if (problemList != null) {
            generateNewProblemList(defaultFileSystem.getPath(resDir), defaultFileSystem.getPath(problemList));
        } else {
            printUsage();
        }

    }

    static String [] getNameAndExtension(Path fileName) {
        String[] tokens = fileName.toString().split("\\.(?=[^\\.]+$)");
        return tokens;
    }

    private static void generateNewProblemList(Path resDir, Path path) {
        File dir = resDir.toFile();
        if (!dir.exists()) {
            dir.mkdir();
        }
        Path fileName = path.getFileName();
        Path pathToFixedIssues = defaultFileSystem.getPath(dir.toString(), getNameAndExtension(fileName)[0] + "."
                + "html");
        Path pathToTestIssues = defaultFileSystem.getPath(dir.toString(), "test_issues.html");
        System.out.println("generating file with fixed issues: " + pathToFixedIssues);
        System.out.println("generating file with test issues: " + pathToFixedIssues);
        writerFixedIssues = new TextFileWriter(pathToFixedIssues);
        writerTestIssues = new TextFileWriter(pathToTestIssues);


        try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            writerFixedIssues.writeln("");
            writerTestIssues.writeln("");

            //writeHeader(writerFixedIssues);
            //writeHeader(writerTestIssues);
            writerFixedIssues = new TextFileWriter(pathToFixedIssues, true);
            writerTestIssues = new TextFileWriter(pathToTestIssues,true);

            writerFixedIssues.writeln("<table>");
            writerFixedIssues.writeln("<tr><td></td><td>Bug</td><td>Description</td><td>Status</td><td>Resolution</td><td>fixVersion</td></tr>");

            writerTestIssues.writeln("<table>");
            writerTestIssues.writeln("<tr><td></td><td>Bug</td><td>Description</td><td>Status</td><td>Resolution</td><td>fixVersion</td></tr>");

            while ((line = br.readLine()) != null) {
                if (line.startsWith("# https://bugs.openjdk.java.net/browse/JDK-")) {
                    String issueKey = getIsssueKey(line);
                    System.out.println(issueKey);
                    getStatusOpenJDKIssue(issueKey);
                }
            }

            writerFixedIssues.writeln("</table>");
            writerTestIssues.writeln("</table>");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeHeader(TextFileWriter writer) throws IOException {
        writer.writeln("");
        writer.writeln("<table>");
        writer.writeln("<tr><td></td><td>Bug</td><td>Description</td><td>Status</td><td>Resolution</td><td>fixVersion</td></tr>");
    }

    private static void getStatusOpenJDKIssue(String issueKey) throws IOException {
        String url = "https://bugs.openjdk.java.net/sr/jira.issueviews:searchrequest-xml/temp/SearchRequest.xml?jqlQuery=issuekey%3D"
                + issueKey;

        URL obj = null;
        try {
            obj = new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        HttpURLConnection con = null;
        con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        try {
            con.setRequestMethod("GET");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }

        //add request header
        con.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        parseResponse(new ByteArrayInputStream(
                response.toString().getBytes("UTF-8")));
    }

    private static void parseResponse(ByteArrayInputStream byteArrayInputStream) {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(byteArrayInputStream);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("item");
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String link = getTextContent(eElement, "link");
                    String key = getTextContent(eElement, "key");
                    String description = getTextContent(eElement, "summary");
                    String status = getTextContent(eElement, "status");
                    String status_id = getAttribute(eElement, "status", "id");
                    String resolution = getTextContent(eElement, "resolution");
                    String resolution_id = getAttribute(eElement, "resolution", "id");
                    String fixVersion = getTextContent(eElement, "fixVersion");
                    int res_id = Integer.decode(resolution_id);

                    if (res_id > 0) {
                        countFixed++;
                        System.out.println(link + " (status: " + status
                                + "[" + status_id + "];\t" + "resolution: " + resolution + "[" + resolution_id + "])");
                        writerFixedIssues.writeln("<tr><td>" + countFixed
                                + "</td><td style=\"white-space: nowrap\"><a href=\"" + link + "\">" + key + "</a></td>"
                                + "<td>" + description + "</td>" + "<td style=\"white-space: nowrap\">" + status
                                + "[" + status_id + "]</td>" + "<td>"
                                + resolution + "[" + resolution_id + "]</td><td>" + fixVersion + "</td></tr>");
                    }
                    if (description.startsWith("[TEST_BUG]")) {
                        countTestIssues++;
                        System.out.println(link + " (status: " + status
                                + "[" + status_id + "];\t" + "resolution: " + resolution + "[" + resolution_id + "])");
                        writerTestIssues.writeln("<tr><td>" + countTestIssues
                                + "</td><td style=\"white-space: nowrap\"><a href=\"" + link + "\">" + key + "</a></td>"
                                + "<td>" + description + "</td>" + "<td style=\"white-space: nowrap\">" + status
                                + "[" + status_id + "]</td>" + "<td>"
                                + resolution + "[" + resolution_id + "]</td><td>" + fixVersion + "</td></tr>");

                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    private static String getTextContent(Element eElement, String nodeName) {
        try {
            return eElement.getElementsByTagName(nodeName).item(0).getTextContent();
        } catch (NullPointerException e) {
            return "";
        }
    }

    private static String getAttribute(Element eElement, String nodeName, String attributeName) {
        try {
            return ((Element) eElement.getElementsByTagName(nodeName).item(0)).getAttribute(attributeName);
        } catch (NullPointerException e) {
            return "";
        }
    }

    private static String getIsssueKey(String line) {
        String issueKey = "JDK-" + line.split("-")[1];
        return issueKey.split(" ")[0];
    }
}

class TextFileWriter {
    private Path path;
    private boolean append_to_file = false;

    public TextFileWriter(Path path) {
        this.path = path;
    }

    public TextFileWriter(Path path, boolean append_to_file) {
        this.path = path;
        this.append_to_file = append_to_file;
    }

    public void writeln(String textLine) throws IOException {
        FileWriter write = new FileWriter(path.toString(), append_to_file);
        PrintWriter printWriter = new PrintWriter(write);
        printWriter.printf("%s" + "%n", textLine);
        printWriter.close();
    }
}