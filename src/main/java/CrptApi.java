import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.sun.net.httpserver.HttpServer;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CrptApi {

    private static final Integer serverPort = 8000;
    private static final Path storagePath = Paths.get("src", "main", "resources", "DB.json");
    private final static ObjectMapper objectMapper = new ObjectMapper();

    private final Integer requestLimit;
    private AtomicInteger currentRequestSize;

    public CrptApi(TimeUnit timeUnit, Integer requestLimit) {
        this.requestLimit = requestLimit;
        this.currentRequestSize = new AtomicInteger(0);

        //
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> currentRequestSize.set(0), 0, 1, timeUnit);
    }

    private synchronized static void saveDocuments(Document document) {
        try {
            DBStorage storage = getStorage();
            storage.getDocuments().add(document);
            setStorage(storage);
        } catch (IOException e) {
            log.error(String.format("Произошла ошибка сохранения документа: %s", document.getDocId()), e);
        }
    }

    /**
     * Получаем список документов из хранилища
     * @return список документов
     */
    private static DBStorage getStorage() throws IOException {
        return objectMapper.readValue(storagePath.toFile(), DBStorage.class);
    }

    /**
     * Записываем список документов в хранилище
     * @param storage список документов
     */
    private static void setStorage(DBStorage storage) throws IOException {
        objectMapper.writeValue(storagePath.toFile(), storage);
    }

    public void createDocument(Document document) {
        //Если число запросов на сохранение документа не превышено
        if (currentRequestSize.get() < requestLimit) {
            currentRequestSize.incrementAndGet();
            CrptApi.saveDocuments(document);
        } else {
            log.warn(String.format("Запрос на сохранение документа: %s отклонен. Причина: \"превышен лимит запросов\"", document.getDocId()));
        }
    }

    public static void main(String[] args) throws IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);
        HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
        server.createContext("/api/v3/lk/documents/create", (exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                Document registerRequest = objectMapper.readValue(exchange.getRequestBody(), Document.class);
                api.createDocument(registerRequest);

                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        }));
        server.setExecutor(null);
        server.start();
    }
}

@Data
class Description {
    private String participantInn;
}

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class Product {
    private String certificateDocument;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date certificateDocumentDate;
    private String certificateDocumentNumber;
    private String ownerInn;
    private String producerInn;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date productionDate;
    private String tnvedCode;
    private String uitCode;
    private String uituCode;
}

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
class Document {
    private Description description;
    private String docId;
    private String docStatus;
    private DocType docType;
    @JsonProperty("importRequest")
    private Boolean importRequest;
    private String ownerInn;
    private String participantInn;
    private String producerInn;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date productionDate;
    private String productionType;
    private List<Product> products = new ArrayList<>();
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date regDate;
    private String regNumber;
}

@Getter
enum DocType {
    LP_INTRODUCE_GOODS
}

@Data
class DBStorage {
    private List<Document> documents;
}