import com.nymag.imageManagement.model.AbstractRendition;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

@Path("/imageconvert")
@Component(immediate = true
        , metatype = true
        , enabled = true)
@Service(ImageConvert.class)
public class ImageConvert
{
    private static final String RENDITION_MIME_TYPE = "image/jpeg";
    private static final String PARAMETER_RENDITIONS = "renditions";
    private static final String RENDITION_DATA_CHARSET_NAME = "ISO-8859-1";
    private static final String JSON_OBJECT_KEY_RENDITION_RULES = "rendition-rules";
    private static final String JSON_OBJECT_KEY_MIME_TYPE = "mimetype";
    private static final String JSON_OBJECT_KEY_RENDITION_GENERATION_SERVICE_URL = "rendition-generation-service-url";
    private static final String JSON_OBJECT_KEY_RENDITION_IMAGE_HANDLER_GET_ASSET_SERVICE_URL = "rendition-image-handler-get-asset-service-url";
    private static final String JSON_OBJECT_KEY_RENDITION_IMAGE_HANDLER_WRITE_RENDITIONS_SERVICE_URL = "rendition-image-handler-write-renditions-service-url";
    private static final String JSON_OBJECT_KEY_ASSET_WIDTH = "asset-width";
    private static final String JSON_OBJECT_KEY_ASSET_HEIGHT = "asset-height";
    private static final String JSON_OBJECT_KEY_ASSET_NODE_PATH = "asset-node-path";

    private static final Logger log = LoggerFactory.getLogger(ImageConvert.class);

    @GET
    @Path("/rengen")
    @Produces("application/json")
    public String processRengenImageRendition() throws Exception
    {
        File imageFolder = new File("/Users/sli/Work/test_images");
        long beforeTime = System.currentTimeMillis();
        for (final File fileEntry : imageFolder.listFiles())
        {
            if (fileEntry.getName().contains("img"))
            {
                JSONObject renditionParamsJson = new JSONObject();

                String imageName = fileEntry.getName();
                enhanceRenditionParameterJsonWithServiceURlProperties(renditionParamsJson, "http://localhost:9998/imageconvert?assetNodePath=/Users/sli/Work/test_images/" + imageName, "http://localhost:9998/imageconvert/save", "http://rgs.dev.nymag.biz:8383/api");
                enhanceRenditionParameterJsonWithAssetProperties("/Users/sli/Work/test_images/" + imageName, renditionParamsJson);

                // Build JSON object representation of the on-demand rendition rule
                JSONObject renditionRulesJsonObject = buildRenditionRulesJson(imageName);

                // Put the JSON object representation of the on-demand rendition rule in a JSON array (It's in an array because the RGS can process multiple renditions in one call).
                JSONArray renditionRulesJsonArray = new JSONArray();
                renditionRulesJsonArray.put(renditionRulesJsonObject);

                // Finally, update the rendition generator service's parameter JSON object with the rendition rules array:
                renditionParamsJson.put(JSON_OBJECT_KEY_RENDITION_RULES, renditionRulesJsonArray);

                executeHttpPostRequest(renditionParamsJson);
            }
        }
        long afterTime = System.currentTimeMillis();
        long difference = (afterTime - beforeTime);
        System.out.println("elapsed time: " + difference + " milliseconds");
        return null;
    }

    @GET
    @Path("/node")
    @Produces("application/json")
    public String processNodeImageRendition() throws Exception
    {
        File imageFolder = new File("/Users/sli/Work/Source/nodejs-resize-image/img");
        String nodeServiceHost = "http://localhost:8383/";
        long beforeTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++)
        {
            for (final File fileEntry : imageFolder.listFiles())
            {
                if (fileEntry.getName().contains("img"))
                {
                    String imageName = fileEntry.getName();
                    executeHttpPostRequest(nodeServiceHost + imageName + "/417x417+784+845.jpg");
                }
            }
        }
        long afterTime = System.currentTimeMillis();
        long difference = (afterTime - beforeTime);
        System.out.println("elapsed time: " + difference + " milliseconds");
        return null;
    }

    @POST
    @Path("/save")
    @Consumes("application/json")
    public Response saveImageRendition(Object request) throws Exception
    {
        HttpServletRequest request1 = (HttpServletRequest) request;
        // Get byte array representation of the rendition image.
        String renditionDataParameterName = PARAMETER_RENDITIONS + "[0]";
        final byte[] renditionByteArray = request1.getParameter(renditionDataParameterName).getBytes(RENDITION_DATA_CHARSET_NAME);

        // Create the rendition input stream and write it.
        ByteArrayInputStream renditionInputStream = new ByteArrayInputStream(renditionByteArray);

        if (renditionInputStream == null)
        {
            String errorMsg = "[RIHS] An error occured when trying to write rendition '";
            throw new Exception(errorMsg);
        }
        else
        {
        }
        return Response.status(201).entity(renditionInputStream).build();
    }

    private JSONObject buildRenditionRulesJson(String imageName) throws JSONException
    {
        JSONObject renditionRulesJsonObject = new JSONObject();
        renditionRulesJsonObject.put(AbstractRendition.PROP_RESIZE_W, new Long(100));
        renditionRulesJsonObject.put(AbstractRendition.PROP_RESIZE_H, new Long(100));
        renditionRulesJsonObject.put(AbstractRendition.PROP_CROP_X, new Long(784));
        renditionRulesJsonObject.put(AbstractRendition.PROP_CROP_Y, new Long(845));
        renditionRulesJsonObject.put(AbstractRendition.PROP_CROP_W, new Long(417));
        renditionRulesJsonObject.put(AbstractRendition.PROP_CROP_H, new Long(417));
        renditionRulesJsonObject.put(AbstractRendition.PROP_ORDER, new Long(1));
        renditionRulesJsonObject.put(AbstractRendition.PROP_SHOULD_CROP, true);
        renditionRulesJsonObject.put(AbstractRendition.JSON_KEY_NAME, "rengen_convert_" + imageName);
        renditionRulesJsonObject.put(JSON_OBJECT_KEY_MIME_TYPE, RENDITION_MIME_TYPE);
        return renditionRulesJsonObject;
    }


    /**
     * Execute HTTP POST request to Rendition Generation Service.
     *
     * @param renditionArgsJson
     * @throws Exception
     */
    private void executeHttpPostRequest(final JSONObject renditionArgsJson) throws Exception
    {
        // Create rendition writer thread.
        Thread renditionGenerationRequestThread = new Thread(new Runnable()
        {
            public void run()
            {
                String rgsServiceUrl = null;

                try
                {
                    // Execute HTTP POST request to Rendition Generation Service.
                    HttpClient client = new HttpClient();

                    // Set the to make a post call the Rendition Generation Service with the Rendition Parameter JSON as an HTTP POST parameter.
                    rgsServiceUrl = renditionArgsJson.getString(JSON_OBJECT_KEY_RENDITION_GENERATION_SERVICE_URL);

                    PostMethod method = new PostMethod(rgsServiceUrl);
                    method.setRequestHeader("Accept", "application/json");
                    method.setRequestHeader("Content-type", "application/json");

                    // Set request json content to be sent via HTTP POST:
                    StringRequestEntity requestEntity = new StringRequestEntity(renditionArgsJson.toString(), "application/json", null);
                    method.setRequestEntity(requestEntity);

                    int statusCode = client.executeMethod(method);

                    // Log error and throw exception if we've failed to execute the HTTP POST request to the Rendition Generation Service.
                    if (statusCode != 200)
                    {
                        log.error("[IMAGE MANAGEMENT] An error occured while attempting an HTTP POST request to the Rendition Generation Service (" + rgsServiceUrl + "). The returned status code was " + statusCode + ". The following parameters were used with the request: " + renditionArgsJson.toString());
                    }
                } catch (Exception e)
                {
                    log.error("[IMAGE MANAGEMENT] An error occured while attempting an HTTP POST request to the Rendition Generation Service (" + rgsServiceUrl + ") with the following parameters: " + renditionArgsJson.toString(), e);
                }
            }
        });

        renditionGenerationRequestThread.run();
    }

    /**
     * Execute HTTP GET request to Node JS service
     *
     * @param nodeServiceUrl
     * @throws Exception
     */
    private void executeHttpPostRequest(final String nodeServiceUrl) throws Exception
    {
        // Create rendition writer thread.
        Thread renditionGenerationRequestThread = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    // Execute HTTP POST request to Rendition Generation Service.
                    HttpClient client = new HttpClient();

                    GetMethod method = new GetMethod(nodeServiceUrl);

                    int statusCode = client.executeMethod(method);

                    // Log error and throw exception if we've failed to execute the HTTP POST request to the Rendition Generation Service.
                    if (statusCode != 200)
                    {
                        log.error("[IMAGE MANAGEMENT] An error occured." + "The returned status code was " + statusCode + ".");
                    }
                } catch (Exception e)
                {
                    log.error("[IMAGE MANAGEMENT] An error occured.", e);
                }
            }
        });

        renditionGenerationRequestThread.run();
    }

    /**
     * Enhance the rendition parameter json with Asset properties.
     *
     * @param assetNodePath
     * @param renditionParamsJson
     * @throws Exception
     */
    public void enhanceRenditionParameterJsonWithAssetProperties(String assetNodePath, JSONObject renditionParamsJson) throws JSONException
    {
        // Get the asset.
        BufferedImage bimg = null;
        try
        {
            bimg = ImageIO.read(new File(assetNodePath));
        } catch (IOException e)
        {
            log.error("image failed to load: " + assetNodePath);
        }

        if (bimg == null)
        {
            log.error("image is null: " + assetNodePath);
        }
        else
        {
            long assetWidth = (long) bimg.getWidth();
            long assetHeight = (long) bimg.getHeight();
            renditionParamsJson.put(JSON_OBJECT_KEY_ASSET_WIDTH, assetWidth);
            renditionParamsJson.put(JSON_OBJECT_KEY_ASSET_HEIGHT, assetHeight);

            // Asset node path:
            renditionParamsJson.put(JSON_OBJECT_KEY_ASSET_NODE_PATH, assetNodePath);
        }
    }


    /**
     * Enhance the rendition parameter json with service URL properties
     *
     * @param renditionParamsJson
     * @param assetRetrievalServiceUrl
     * @param renditionPersistenceServiceUrl
     * @param renditionGeneratorServiceGatewayUrl
     *
     * @throws Exception
     */
    private void enhanceRenditionParameterJsonWithServiceURlProperties(JSONObject renditionParamsJson, String assetRetrievalServiceUrl, String renditionPersistenceServiceUrl, String renditionGeneratorServiceGatewayUrl) throws Exception
    {
        renditionParamsJson.put(JSON_OBJECT_KEY_RENDITION_IMAGE_HANDLER_GET_ASSET_SERVICE_URL, assetRetrievalServiceUrl);
        renditionParamsJson.put(JSON_OBJECT_KEY_RENDITION_IMAGE_HANDLER_WRITE_RENDITIONS_SERVICE_URL, renditionPersistenceServiceUrl);
        renditionParamsJson.put(JSON_OBJECT_KEY_RENDITION_GENERATION_SERVICE_URL, renditionGeneratorServiceGatewayUrl);
    }


    public static void main(String[] args) throws IOException
    {
        HttpServer server = HttpServerFactory.create("http://localhost:9998/");
        server.start();

        System.out.println("Server running");
        System.out.println("Visit: http://localhost:9998/imageconvert");
        System.out.println("Hit return to stop...");
        System.in.read();
        System.out.println("Stopping server");
        server.stop(0);
        System.out.println("Server stopped");
    }
}                             