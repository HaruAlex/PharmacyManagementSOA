package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Drug;
import models.DrugGroup;
import models.Manufacturer;
import models.Unit;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import repositories.CatalogTestSupport;
import repositories.DrugGroupRepository;
import repositories.DrugRepository;
import repositories.ManufacturerRepository;
import repositories.UnitRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.CONFLICT;
import static play.mvc.Http.Status.CREATED;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.PUT;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/** Route-level tests for HTTP status codes, JSON contracts, and endpoint wiring. */
public class CatalogControllerTest extends CatalogTestSupport {

    @Test
    public void branchCreateAndStatusRoutesWork() {
        Result created = route(app, new Http.RequestBuilder()
                .method(POST)
                .uri("/api/v1/catalog/branches")
                .bodyJson(Json.parse("{\"code\":\"CN901\",\"name\":\"Test\",\"type\":\"STORE\"}")));
        assertEquals(CREATED, created.status());
        long id = responseJson(created).get("id").asLong();

        Result changed = route(app, new Http.RequestBuilder()
                .method("PATCH")
                .uri("/api/v1/catalog/branches/" + id + "/status")
                .bodyJson(Json.parse("{\"status\":\"INACTIVE\"}")));
        assertEquals(OK, changed.status());
        assertEquals("INACTIVE", responseJson(changed).get("status").asText());
    }

    @Test
    public void drugSearchRoutesSupportIngredientAndBarcodeLookup() {
        DrugGroup group = new DrugGroup();
        group.setCode("GRP-901");
        group.setName("Pain relief");
        long groupId = app.injector().instanceOf(DrugGroupRepository.class).create(group).getId();
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Example Pharma");
        long manufacturerId = app.injector().instanceOf(ManufacturerRepository.class).create(manufacturer).getId();
        Unit unit = new Unit();
        unit.setCode("TAB");
        unit.setName("Tablet");
        long unitId = app.injector().instanceOf(UnitRepository.class).create(unit).getId();

        Drug drug = new Drug();
        drug.setCode("D-901");
        drug.setName("Pain tablet");
        drug.setActiveIngredient("Acetaminophen");
        drug.setBarcode("89300901");
        drug.setGroupId(groupId);
        drug.setManufacturerId(manufacturerId);
        drug.setPrimaryUnitId(unitId);
        app.injector().instanceOf(DrugRepository.class).create(drug);

        Result search = route(app, new Http.RequestBuilder()
                .method(GET)
                .uri("/api/v1/catalog/drugs?q=Acetaminophen"));
        assertEquals(OK, search.status());
        assertEquals(1, responseJson(search).get("total").asInt());

        Result barcode = route(app, new Http.RequestBuilder()
                .method(GET)
                .uri("/api/v1/catalog/drugs/by-barcode/89300901"));
        assertEquals(OK, barcode.status());
        assertEquals("D-901", responseJson(barcode).get("code").asText());
    }

    @Test
    public void drugCreateRejectsMissingRequiredForeignKeys() {
        Result result = route(app, new Http.RequestBuilder()
                .method(POST)
                .uri("/api/v1/catalog/drugs")
                .bodyJson(Json.parse("{\"code\":\"D-902\",\"name\":\"Incomplete\"}")));
        assertEquals(BAD_REQUEST, result.status());
        JsonNode response = responseJson(result);
        assertTrue(response.get("message").asText().contains("groupId"));
    }

    @Test
    public void drugUnitConversionsCanBeReplacedAndReturnedInDrugDetails() {
        DrugGroup group = new DrugGroup();
        group.setCode("GRP-902");
        group.setName("Test group");
        long groupId = app.injector().instanceOf(DrugGroupRepository.class).create(group).getId();
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("Test manufacturer");
        long manufacturerId = app.injector().instanceOf(ManufacturerRepository.class).create(manufacturer).getId();
        Unit base = new Unit();
        base.setCode("TAB");
        base.setName("Tablet");
        long baseId = app.injector().instanceOf(UnitRepository.class).create(base).getId();
        Unit box = new Unit();
        box.setCode("BOX");
        box.setName("Box");
        long boxId = app.injector().instanceOf(UnitRepository.class).create(box).getId();

        Drug drug = new Drug();
        drug.setCode("D-903");
        drug.setName("Test medicine");
        drug.setGroupId(groupId);
        drug.setManufacturerId(manufacturerId);
        drug.setPrimaryUnitId(baseId);
        long drugId = app.injector().instanceOf(DrugRepository.class).create(drug).getId();

        Result configured = route(app, new Http.RequestBuilder()
                .method(PUT)
                .uri("/api/v1/catalog/drugs/" + drugId + "/units")
                .bodyJson(Json.parse("{\"units\":[{\"unitId\":" + boxId +
                        ",\"conversionToBase\":10,\"salePrice\":25.50,\"isSaleUnit\":true,\"isDefault\":true}]}")));
        assertEquals(OK, configured.status());
        assertEquals(1, responseJson(configured).get("units").size());

        Result detail = route(app, new Http.RequestBuilder()
                .method(GET)
                .uri("/api/v1/catalog/drugs/" + drugId));
        assertEquals(OK, detail.status());
        assertEquals(25.50, responseJson(detail).get("units").get(0).get("salePrice").asDouble(), 0.001);

        Result invalid = route(app, new Http.RequestBuilder()
                .method(PUT)
                .uri("/api/v1/catalog/drugs/" + drugId + "/units")
                .bodyJson(Json.parse("{\"units\":[{\"unitId\":" + boxId + ",\"conversionToBase\":0}]}")));
        assertEquals(BAD_REQUEST, invalid.status());
        assertEquals(1, responseJson(route(app, new Http.RequestBuilder()
                .method(GET)
                .uri("/api/v1/catalog/drugs/" + drugId))).get("units").size());
    }

    @Test
    public void customerCreateGeneratesCodeAndRejectsDuplicatePhone() {
        Result first = route(app, new Http.RequestBuilder()
                .method(POST)
                .uri("/api/v1/catalog/customers")
                .bodyJson(Json.parse("{\"name\":\"Nguyen An\",\"phone\":\"0900000199\",\"allergies\":\"Penicillin\"}")));
        assertEquals(CREATED, first.status());
        assertTrue(responseJson(first).get("code").asText().startsWith("KH"));
        assertEquals("Penicillin", responseJson(first).get("allergies").asText());

        Result duplicate = route(app, new Http.RequestBuilder()
                .method(POST)
                .uri("/api/v1/catalog/customers")
                .bodyJson(Json.parse("{\"name\":\"Another customer\",\"phone\":\"0900000199\"}")));
        assertEquals(CONFLICT, duplicate.status());
    }

    @Test
    public void customerSearchRejectsInvalidPageRatherThanReturningServerError() {
        Result result = route(app, new Http.RequestBuilder()
                .method(GET)
                .uri("/api/v1/catalog/customers?page=zero"));
        assertEquals(BAD_REQUEST, result.status());
    }

    private JsonNode responseJson(Result result) {
        return Json.parse(contentAsString(result));
    }
}
