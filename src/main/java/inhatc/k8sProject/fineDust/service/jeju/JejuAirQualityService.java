package inhatc.k8sProject.fineDust.service.jeju;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inhatc.k8sProject.fineDust.domain.jeju.JejuAirQuality;
import inhatc.k8sProject.fineDust.repository.jeju.JejuAirQualityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class JejuAirQualityService {

    private final JejuAirQualityRepository jejuAirQualityRepository;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위해 추가
    private static final Logger log = LoggerFactory.getLogger(JejuAirQualityService.class);

    //프로퍼티에서 API 키 값을 받아오는 어노테이션
    @Value("${service.key}")
    private String serviceKey;


    @Scheduled(cron = "0 10 * * * *")
    public void updateAirQualityDataAutomatically() {
        String sidoName = "제주"; // 제주도의 시도명
        fetchAndSaveJejuAirQualityData(sidoName); // 제주도 대기 질 데이터 가져와 저장
    }
    //--------------------------------------------------------------------------------------------------------------------------------------

    // 제주 지역 대기 질 데이터를 가져와 저장하는 메서드
    @Transactional("jejuTransactionManager")
    public void fetchAndSaveJejuAirQualityData(String sidoName) {
        try {
            String requestUrlBuilder = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty?" + "sidoName=" + URLEncoder.encode(sidoName, StandardCharsets.UTF_8) +
                    "&pageNo=" + URLEncoder.encode("1", StandardCharsets.UTF_8) +
                    "&numOfRows=" + URLEncoder.encode("100", StandardCharsets.UTF_8) +
                    "&returnType=" + URLEncoder.encode("json", StandardCharsets.UTF_8) +
                    "&serviceKey=" + serviceKey + // 서비스키는 사용자의 실제 키로 변경해야 합니다.
                    "&ver=" + URLEncoder.encode("1.0", StandardCharsets.UTF_8);

            URL url = new URL(requestUrlBuilder);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/json");

            // HTTP 응답 코드 확인
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.error("HTTP 오류 코드 : " + conn.getResponseCode());
            }

            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                responseBuilder.append(line);
            }
            rd.close();
            conn.disconnect();

            String response = responseBuilder.toString();
            // 응답 형식 검증
            if (!response.trim().startsWith("{")) {
                log.error("예상하지 않은 JSON 형식의 응답입니다. 응답: " + response);
            }

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode items = rootNode.path("response").path("body").path("items");

            items.forEach(item -> {
                JejuAirQuality jejuAirQuality = parseAirQualityData(item);
                jejuAirQualityRepository.save(jejuAirQuality);
            });

        } catch (IOException e) {
            log.error("대기 질 데이터를 가져오고 저장하는 데 실패했습니다", e);
        }
    }
    //--------------------------------------------------------------------------------------------------------------------------------------

    // JSON 데이터를 파싱하여 JejuAirQuality 객체로 변환하는 메서드
    private JejuAirQuality parseAirQualityData(JsonNode item) {
        JejuAirQuality jejuAirQuality = new JejuAirQuality();

        // 측정 시간 설정: API로부터 받은 시간을 파싱하여 LocalDateTime 객체로 변환하고, KST(한국 표준시)로 조정

        // 측정 시간을 파싱하고 KST(Korean Standard Time)로 조정
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String dateTimeStr = item.path("dataTime").asText(null);
        LocalDateTime dateTime = null;
        if (dateTimeStr != null) {
            try {
                dateTime = LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                log.error("날짜 시간을 구문 분석하지 못했습니다: " + dateTimeStr, e);
                // 오류 처리 또는 기본 날짜 시간 설정
                dateTime = LocalDateTime.now(); // 예시 기본값, 필요에 따라 조정
            }
        } else {
            // null 경우 처리 또는 기본 날짜 시간 설정
            dateTime = LocalDateTime.now(); // 예시 기본값, 필요에 따라 조정
        }
        LocalDateTime kstDateTime = dateTime.plusHours(9); // KST로 변환
        jejuAirQuality.setDataTime(kstDateTime); // 측정 시간 설정

        // 나머지 값들 설정: JSON 객체에서 각 대기 질 지표 값을 읽어와서 JejuAirQuality 객체의 속성으로 설정
        jejuAirQuality.setStationName(item.path("stationName").asText(null)); // 측정소 이름
        jejuAirQuality.setSidoName(item.path("sidoName").asText(null)); // 지역 이름
        jejuAirQuality.setPm10Value(item.path("pm10Value").asDouble(0)); // PM10 값
        jejuAirQuality.setPm25Value(item.path("pm25Value").asDouble(0)); // PM10 값
        return jejuAirQuality; // 설정된 값을 담은 JejuAirQuality 객체 반환
    }
    //--------------------------------------------------------------------------------------------------------------------------------------


}
