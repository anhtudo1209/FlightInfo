package vn.edu.usth.flightinfo;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONObject;

public class FlightDetailFragment extends BottomSheetDialogFragment {

    private static final String ARG_FLIGHT_JSON = "arg_flight_json";

    private TextView textBasicInfo, textAirline, textRoute, textProgress, textAlt, textSpeed, textReg;
    private TextView depAirport, depCodes, depTerminal, depBaggage, depTimes, depRunways;
    private TextView arrAirport, arrCodes, arrTerminal, arrBaggage, arrTimes, arrRunways;
    private TextView flightNumber, flightCodeshared, airlineName;
    private TextView aircraftReg, aircraftCodes, aircraftModel;
    private TextView liveLatlon, liveAlt, liveSpeed, liveHeading, liveIsGround, liveUpdated;

    public static FlightDetailFragment newInstance(String flightJsonString) {
        FlightDetailFragment f = new FlightDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FLIGHT_JSON, flightJsonString);
        f.setArguments(args);
        return f;
    }
    public FlightDetailFragment() {}
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.NoDimBottomSheet);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog dlog = (BottomSheetDialog) d;
            View bottomSheet = dlog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
                BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setPeekHeight((int) (getResources().getDisplayMetrics().density * 180));
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                behavior.setSkipCollapsed(false);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_flight_detail, container, false);

        textBasicInfo = v.findViewById(R.id.textBasicInfo);
        textAirline = v.findViewById(R.id.textAirline);
        textRoute = v.findViewById(R.id.textRoute);
        textProgress = v.findViewById(R.id.textProgress);
        textAlt = v.findViewById(R.id.textAlt);
        textSpeed = v.findViewById(R.id.textSpeed);
        textReg = v.findViewById(R.id.textReg);

        depAirport = v.findViewById(R.id.dep_airport);
        depCodes = v.findViewById(R.id.dep_codes);
        depTerminal = v.findViewById(R.id.dep_terminal);
        depBaggage = v.findViewById(R.id.dep_baggage);
        depTimes = v.findViewById(R.id.dep_times);
        depRunways = v.findViewById(R.id.dep_runways);

        arrAirport = v.findViewById(R.id.arr_airport);
        arrCodes = v.findViewById(R.id.arr_codes);
        arrTerminal = v.findViewById(R.id.arr_terminal);
        arrBaggage = v.findViewById(R.id.arr_baggage);
        arrTimes = v.findViewById(R.id.arr_times);
        arrRunways = v.findViewById(R.id.arr_runways);

        flightNumber = v.findViewById(R.id.flight_number);
        flightCodeshared = v.findViewById(R.id.flight_codeshared);
        airlineName = v.findViewById(R.id.airline_name);

        aircraftReg = v.findViewById(R.id.aircraft_reg);
        aircraftCodes = v.findViewById(R.id.aircraft_codes);
        aircraftModel = v.findViewById(R.id.aircraft_model);

        liveLatlon = v.findViewById(R.id.live_latlon);
        liveAlt = v.findViewById(R.id.live_alt);
        liveSpeed = v.findViewById(R.id.live_speed);
        liveHeading = v.findViewById(R.id.live_heading);
        liveIsGround = v.findViewById(R.id.live_isground);
        liveUpdated = v.findViewById(R.id.live_updated);

        if (getArguments() != null && getArguments().containsKey(ARG_FLIGHT_JSON)) {
            String json = getArguments().getString(ARG_FLIGHT_JSON);
            if (json != null) {
                try {
                    JSONObject flight = new JSONObject(json);
                    updateFromJson(flight);
                } catch (Exception e) {
                    android.util.Log.e("FlightDetail", "Error parsing flight JSON", e);
                }
            } else {
                clearFields();
            }
        } else {
            clearFields();
        }

        return v;
    }

    public void updateFromJson(@NonNull JSONObject flight) {
        if (!isAdded() || getView() == null) return;
        if (flight == null || flight.length() == 0) {
            clearFields();
            return;
        }
        try {
            String flightDate = flight.optString("flight_date", "No info");
            String flightStatus = flight.optString("flight_status", "No info");

            JSONObject dep = flight.optJSONObject("departure");
            JSONObject arr = flight.optJSONObject("arrival");
            JSONObject airline = flight.optJSONObject("airline");
            JSONObject flightObj = flight.optJSONObject("flight");
            JSONObject aircraft = flight.optJSONObject("aircraft");
            JSONObject live = flight.optJSONObject("live");

            String depAirportS = dep != null ? dep.optString("airport", "No info") : "No info";
            String depIata = dep != null ? dep.optString("iata", "No info") : "No info";
            String depIcao = dep != null ? dep.optString("icao", "No info") : "No info";
            String depTerminalS = dep != null ? dep.optString("terminal", "No info") : "No info";
            String depGate = dep != null ? dep.optString("gate", "No info") : "No info";
            String depBaggageS = dep != null ? dep.optString("baggage", "No info") : "No info";
            String depScheduled = dep != null ? dep.optString("scheduled", "No info") : "No info";
            String depEstimated = dep != null ? dep.optString("estimated", "No info") : "No info";
            String depActual = dep != null ? dep.optString("actual", "No info") : "No info";
            String depDelay = dep != null ? (dep.has("delay") ? String.valueOf(dep.opt("delay")) : "No info") : "No info";
            String depEstRunway = dep != null ? dep.optString("estimated_runway", "No info") : "No info";
            String depActRunway = dep != null ? dep.optString("actual_runway", "No info") : "No info";

            String arrAirportS = arr != null ? arr.optString("airport", "No info") : "No info";
            String arrIata = arr != null ? arr.optString("iata", "No info") : "No info";
            String arrIcao = arr != null ? arr.optString("icao", "No info") : "No info";
            String arrTerminalS = arr != null ? arr.optString("terminal", "No info") : "No info";
            String arrGate = arr != null ? arr.optString("gate", "No info") : "No info";
            String arrBaggageS = arr != null ? arr.optString("baggage", "No info") : "No info";
            String arrScheduled = arr != null ? arr.optString("scheduled", "No info") : "No info";
            String arrEstimated = arr != null ? arr.optString("estimated", "No info") : "No info";
            String arrActual = arr != null ? arr.optString("actual", "No info") : "No info";
            String arrDelay = arr != null ? (arr.has("delay") ? String.valueOf(arr.opt("delay")) : "No info") : "No info";
            String arrEstRunway = arr != null ? arr.optString("estimated_runway", "No info") : "No info";
            String arrActRunway = arr != null ? arr.optString("actual_runway", "No info") : "No info";

            String airlineNameS = airline != null ? airline.optString("name", "No info") : "No info";
            String airlineIataS = airline != null ? airline.optString("iata", "No info") : "No info";
            String airlineIcaoS = airline != null ? airline.optString("icao", "No info") : "No info";

            String flightNumberS = flightObj != null ? flightObj.optString("number", "No info") : "No info";
            String flightIataS = flightObj != null ? flightObj.optString("iata", "No info") : "No info";
            String flightIcaoS = flightObj != null ? flightObj.optString("icao", "No info") : "No info";
            String codesharedS = "No info";
            if (flightObj != null && flightObj.has("codeshared") && !flightObj.isNull("codeshared")) {
                codesharedS = flightObj.optString("codeshared", "No info");
            }

            String aircraftRegS = aircraft != null ? aircraft.optString("registration", "No info") : "No info";
            String aircraftIcaoS = aircraft != null ? aircraft.optString("icao", "No info") : "No info";
            String aircraftIataS = aircraft != null ? aircraft.optString("iata", "No info") : "No info";
            String aircraftModelS = aircraft != null ? aircraft.optString("model", "No info") : "No info";

            String liveLat = "No info";
            String liveLon = "No info";
            String liveAltS = "No info";
            String liveSpeedH = "No info";
            String liveHdg = "No info";
            String liveIsGroundS = "No info";
            String liveUpdatedS = "No info";

            if (live != null) {
                double lat = live.optDouble("latitude", Double.NaN);
                double lon = live.optDouble("longitude", Double.NaN);
                liveLat = Double.isNaN(lat) ? "No info" : String.valueOf(lat);
                liveLon = Double.isNaN(lon) ? "No info" : String.valueOf(lon);

                double alt = live.optDouble("altitude", Double.NaN);
                liveAltS = Double.isNaN(alt) ? "No info" : String.valueOf(alt);

                double spdH = live.optDouble("speed_horizontal", Double.NaN);
                liveSpeedH = Double.isNaN(spdH) ? "No info" : String.valueOf(spdH);

                double hdg = live.optDouble("heading", Double.NaN);
                liveHdg = Double.isNaN(hdg) ? "No info" : String.valueOf(hdg);

                if (live.has("is_ground")) {
                    liveIsGroundS = String.valueOf(live.optBoolean("is_ground", false));
                }

                liveUpdatedS = live.optString("updated", "No info");
            }

            String header = "Flight " + flightNumberS + " (" + flightDate + ")";
            String route = depAirportS + " → " + arrAirportS;
            String progress = "Status: " + flightStatus;

            textBasicInfo.setText(header);
            textAirline.setText(airlineNameS);
            textRoute.setText(route);
            textProgress.setText(progress);
            textAlt.setText("ALTITUDE\n" + (liveAltS.equals("No info") ? "-" : liveAltS));
            textSpeed.setText("SPEED\n" + (liveSpeedH.equals("No info") ? "-" : liveSpeedH));
            textReg.setText("REG\n" + (aircraftRegS.equals("No info") ? "-" : aircraftRegS));

            depAirport.setText("Airport: " + depAirportS);
            depCodes.setText("IATA / ICAO: " + depIata + " / " + depIcao);
            depTerminal.setText("Terminal / Gate: " + depTerminalS + " / " + depGate);
            depBaggage.setText("Baggage / Delay: " + depBaggageS + " / " + depDelay);
            depTimes.setText("Scheduled / Estimated / Actual: " + depScheduled + " / " + depEstimated + " / " + depActual);
            depRunways.setText("Estimated runway / Actual runway: " + depEstRunway + " / " + depActRunway);

            arrAirport.setText("Airport: " + arrAirportS);
            arrCodes.setText("IATA / ICAO: " + arrIata + " / " + arrIcao);
            arrTerminal.setText("Terminal / Gate: " + arrTerminalS + " / " + arrGate);
            arrBaggage.setText("Baggage / Delay: " + arrBaggageS + " / " + arrDelay);
            arrTimes.setText("Scheduled / Estimated / Actual: " + arrScheduled + " / " + arrEstimated + " / " + arrActual);
            arrRunways.setText("Estimated runway / Actual runway: " + arrEstRunway + " / " + arrActRunway);

            flightNumber.setText("Number / IATA / ICAO: " + flightNumberS + " / " + flightIataS + " / " + flightIcaoS);
            flightCodeshared.setText("Codeshared: " + codesharedS);
            airlineName.setText("Airline: " + airlineNameS + " (IATA: " + airlineIataS + " / ICAO: " + airlineIcaoS + ")");

            aircraftReg.setText("Registration: " + aircraftRegS);
            aircraftCodes.setText("ICAO / IATA: " + aircraftIcaoS + " / " + aircraftIataS);
            aircraftModel.setText("Model: " + aircraftModelS);

            liveLatlon.setText("Lat / Lon: " + liveLat + " / " + liveLon);
            liveAlt.setText("Altitude: " + (liveAltS.equals("No info") ? "-" : liveAltS));
            liveSpeed.setText("Speed H: " + (liveSpeedH.equals("No info") ? "-" : liveSpeedH));
            liveHeading.setText("Heading: " + liveHdg);
            liveIsGround.setText("IsGround: " + liveIsGroundS);
            liveUpdated.setText("Updated: " + liveUpdatedS);

        } catch (Exception e) {
            android.util.Log.e("FlightDetail", "Error updating flight details", e);
        }
    }
    public void clearFields() {
        if (!isAdded() || getView() == null) return;
        textBasicInfo.setText("Flight: -");
        textAirline.setText("-");
        textRoute.setText("- → -");
        textProgress.setText("Status: -");
        textAlt.setText("ALTITUDE\n-");
        textSpeed.setText("SPEED\n-");
        textReg.setText("REG\n-");

        depAirport.setText("Airport: -");
        depCodes.setText("IATA / ICAO: - / -");
        depTerminal.setText("Terminal / Gate: - / -");
        depBaggage.setText("Baggage / Delay: - / -");
        depTimes.setText("Scheduled / Estimated / Actual: - / - / -");
        depRunways.setText("Estimated runway / Actual runway: - / -");

        arrAirport.setText("Airport: -");
        arrCodes.setText("IATA / ICAO: - / -");
        arrTerminal.setText("Terminal / Gate: - / -");
        arrBaggage.setText("Baggage / Delay: - / -");
        arrTimes.setText("Scheduled / Estimated / Actual: - / - / -");
        arrRunways.setText("Estimated runway / Actual runway: - / -");

        flightNumber.setText("Number / IATA / ICAO: - / - / -");
        flightCodeshared.setText("Codeshared: -");
        airlineName.setText("Airline: - (IATA: - / ICAO: -)");

        aircraftReg.setText("Registration: -");
        aircraftCodes.setText("ICAO / IATA: - / -");
        aircraftModel.setText("Model: -");

        liveLatlon.setText("Lat / Lon: - / -");
        liveAlt.setText("Altitude: -");
        liveSpeed.setText("Speed H: -");
        liveHeading.setText("Heading: -");
        liveIsGround.setText("IsGround: -");
        liveUpdated.setText("Updated: -");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        textBasicInfo = textAirline = textRoute = textProgress = textAlt = textSpeed = textReg = null;
        depAirport = depCodes = depTerminal = depBaggage = depTimes = depRunways = null;
        arrAirport = arrCodes = arrTerminal = arrBaggage = arrTimes = arrRunways = null;
        flightNumber = flightCodeshared = airlineName = null;
        aircraftReg = aircraftCodes = aircraftModel = null;
        liveLatlon = liveAlt = liveSpeed = liveHeading = liveIsGround = liveUpdated = null;
    }
}
