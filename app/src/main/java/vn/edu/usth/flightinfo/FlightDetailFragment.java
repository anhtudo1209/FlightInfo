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

    // header + quick fields
    private TextView textBasicInfo, textAirline, textRoute, textProgress, textAlt, textSpeed, textReg;

    // structured detail fields (same ids as layout)
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
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // allow the dialog to expand to full screen later
        dialog.setOnShowListener(d -> {
            BottomSheetDialog dlog = (BottomSheetDialog) d;
            View bottomSheet = dlog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // set full height so expanding covers the screen
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
                BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(bottomSheet);
                // start collapsed; user can expand to full-screen
                behavior.setPeekHeight((int) (getResources().getDisplayMetrics().density * 120)); // 120dp
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

        // If fragment was created with JSON, populate UI
        if (getArguments() != null && getArguments().containsKey(ARG_FLIGHT_JSON)) {
            String json = getArguments().getString(ARG_FLIGHT_JSON);
            if (json != null) {
                try {
                    JSONObject flight = new JSONObject(json);
                    updateFromJson(flight);
                } catch (Exception ignored) {}
            } else {
                clearFields();
            }
        } else {
            clearFields();
        }

        return v;
    }

    /** Set UI fields from a flight JSONObject (Aviationstack flight object). */
    /** Set UI fields from a flight JSONObject (Aviationstack flight object). */
    public void updateFromJson(@NonNull JSONObject flight) {
        if (!isAdded() || getView() == null) return;

        if (flight == null || flight.length() == 0) {
            clearFields();
            return;
        }

        try {
            // Top-level
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
            String codesharedS = flightObj != null ? (flightObj.has("codeshared") && !flightObj.isNull("codeshared") ? flightObj.optString("codeshared","No info") : "No info") : "No info";

            String aircraftRegS = aircraft != null ? aircraft.optString("registration", "No info") : "No info";
            String aircraftIcaoS = aircraft != null ? aircraft.optString("icao", "No info") : "No info";
            String aircraftIataS = aircraft != null ? aircraft.optString("iata", "No info") : "No info";
            String aircraftModelS = aircraft != null ? aircraft.optString("model", "No info") : "No info";

            // live
            final String[] liveLat = {"No info"};
            final String[] liveLon = {"No info"};
            final String[] liveAltS = {"No info"};
            final String[] liveSpeedH = {"No info"};
            final String[] liveSpeedV = {"No info"};
            final String[] liveHdg = {"No info"};
            final String[] liveIsGroundS = {"No info"};
            final String[] liveUpdatedS = {"No info"};

            if (live != null) {
                double lat = live.optDouble("latitude", Double.NaN);
                double lon = live.optDouble("longitude", Double.NaN);
                liveLat[0] = Double.isNaN(lat) ? "No info" : String.valueOf(lat);
                liveLon[0] = Double.isNaN(lon) ? "No info" : String.valueOf(lon);

                double alt = live.optDouble("altitude", Double.NaN);
                liveAltS[0] = Double.isNaN(alt) ? "No info" : String.valueOf(alt);

                double spdH = live.optDouble("speed_horizontal", Double.NaN);
                liveSpeedH[0] = Double.isNaN(spdH) ? "No info" : String.valueOf(spdH);

                double spdV = live.optDouble("speed_vertical", Double.NaN);
                liveSpeedV[0] = Double.isNaN(spdV) ? "No info" : String.valueOf(spdV);

                double hdg = live.optDouble("heading", Double.NaN);
                liveHdg[0] = Double.isNaN(hdg) ? "No info" : String.valueOf(hdg);

                if (live.has("is_ground")) {
                    liveIsGroundS[0] = String.valueOf(live.optBoolean("is_ground", false));
                }

                liveUpdatedS[0] = live.optString("updated", "No info");
            }

            // build display strings
            String header = "Flight " + flightNumberS + " (" + flightDate + ")";
            String route = depAirportS + " → " + arrAirportS;
            String progress = "Status: " + flightStatus;

            // update UI
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                try {
                    if (textBasicInfo != null) textBasicInfo.setText(header);
                    if (textAirline != null) textAirline.setText(airlineNameS);
                    if (textRoute != null) textRoute.setText(route);
                    if (textProgress != null) textProgress.setText(progress);
                    if (textAlt != null) textAlt.setText("ALT\n" + (liveAltS[0].equals("No info") ? "-" : liveAltS[0]));
                    if (textSpeed != null) textSpeed.setText("SPD\n" + (liveSpeedH[0].equals("No info") ? "-" : liveSpeedH[0]));
                    if (textReg != null) textReg.setText("REG\n" + (aircraftRegS.equals("No info") ? "-" : aircraftRegS));

                    // structured fields
                    if (depAirport != null) depAirport.setText("Airport: " + depAirportS);
                    if (depCodes != null) depCodes.setText("IATA / ICAO: " + depIata + " / " + depIcao);
                    if (depTerminal != null) depTerminal.setText("Terminal / Gate: " + depTerminalS + " / " + depGate);
                    if (depBaggage != null) depBaggage.setText("Baggage / Delay: " + depBaggageS + " / " + depDelay);
                    if (depTimes != null) depTimes.setText("Scheduled / Estimated / Actual: " + depScheduled + " / " + depEstimated + " / " + depActual);
                    if (depRunways != null) depRunways.setText("Estimated runway / Actual runway: " + depEstRunway + " / " + depActRunway);

                    if (arrAirport != null) arrAirport.setText("Airport: " + arrAirportS);
                    if (arrCodes != null) arrCodes.setText("IATA / ICAO: " + arrIata + " / " + arrIcao);
                    if (arrTerminal != null) arrTerminal.setText("Terminal / Gate: " + arrTerminalS + " / " + arrGate);
                    if (arrBaggage != null) arrBaggage.setText("Baggage / Delay: " + arrBaggageS + " / " + arrDelay);
                    if (arrTimes != null) arrTimes.setText("Scheduled / Estimated / Actual: " + arrScheduled + " / " + arrEstimated + " / " + arrActual);
                    if (arrRunways != null) arrRunways.setText("Estimated runway / Actual runway: " + arrEstRunway + " / " + arrActRunway);

                    if (flightNumber != null) flightNumber.setText("Number / IATA / ICAO: " + flightNumberS + " / " + flightIataS + " / " + flightIcaoS);
                    if (flightCodeshared != null) flightCodeshared.setText("Codeshared: " + codesharedS);
                    if (airlineName != null) airlineName.setText("Airline: " + airlineNameS + " (IATA: " + airlineIataS + " / ICAO: " + airlineIcaoS + ")");

                    if (aircraftReg != null) aircraftReg.setText("Registration: " + aircraftRegS);
                    if (aircraftCodes != null) aircraftCodes.setText("ICAO / IATA: " + aircraftIcaoS + " / " + aircraftIataS);
                    if (aircraftModel != null) aircraftModel.setText("Model: " + aircraftModelS);

                    if (liveLatlon != null) liveLatlon.setText("Lat / Lon: " + liveLat[0] + " / " + liveLon[0]);
                    if (liveAlt != null) liveAlt.setText("Altitude: " + (liveAltS[0].equals("No info") ? "-" : liveAltS[0]));
                    if (liveSpeed != null) liveSpeed.setText("Speed H / V: " + (liveSpeedH[0].equals("No info") ? "-" : liveSpeedH[0]) + " / " + (liveSpeedV[0].equals("No info") ? "-" : liveSpeedV[0]));
                    if (liveHeading != null) liveHeading.setText("Heading: " + liveHdg[0]);
                    if (liveIsGround != null) liveIsGround.setText("IsGround: " + liveIsGroundS[0]);
                    if (liveUpdated != null) liveUpdated.setText("Updated: " + liveUpdatedS[0]);
                } catch (Exception ignored) {}
            });

        } catch (Exception ignored) {}
    }
    public void clearFields() {
        // Only try to touch views when the fragment is added and view exists
        if (!isAdded() || getView() == null) return;

        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                if (textBasicInfo != null) textBasicInfo.setText("Flight: -");
                if (textAirline != null) textAirline.setText("-");
                if (textRoute != null) textRoute.setText("- → -");
                if (textProgress != null) textProgress.setText("Status: -");
                if (textAlt != null) textAlt.setText("ALT\n-");
                if (textSpeed != null) textSpeed.setText("SPD\n-");
                if (textReg != null) textReg.setText("REG\n-");

                if (depAirport != null) depAirport.setText("Airport: -");
                if (depCodes != null) depCodes.setText("IATA / ICAO: - / -");
                if (depTerminal != null) depTerminal.setText("Terminal / Gate: - / -");
                if (depBaggage != null) depBaggage.setText("Baggage / Delay: - / -");
                if (depTimes != null) depTimes.setText("Scheduled / Estimated / Actual: - / - / -");
                if (depRunways != null) depRunways.setText("Estimated runway / Actual runway: - / -");

                if (arrAirport != null) arrAirport.setText("Airport: -");
                if (arrCodes != null) arrCodes.setText("IATA / ICAO: - / -");
                if (arrTerminal != null) arrTerminal.setText("Terminal / Gate: - / -");
                if (arrBaggage != null) arrBaggage.setText("Baggage / Delay: - / -");
                if (arrTimes != null) arrTimes.setText("Scheduled / Estimated / Actual: - / - / -");
                if (arrRunways != null) arrRunways.setText("Estimated runway / Actual runway: - / -");

                if (flightNumber != null) flightNumber.setText("Number / IATA / ICAO: - / - / -");
                if (flightCodeshared != null) flightCodeshared.setText("Codeshared: -");
                if (airlineName != null) airlineName.setText("Airline: - (IATA: - / ICAO: -)");

                if (aircraftReg != null) aircraftReg.setText("Registration: -");
                if (aircraftCodes != null) aircraftCodes.setText("ICAO / IATA: - / -");
                if (aircraftModel != null) aircraftModel.setText("Model: -");

                if (liveLatlon != null) liveLatlon.setText("Lat / Lon: - / -");
                if (liveAlt != null) liveAlt.setText("Altitude: -");
                if (liveSpeed != null) liveSpeed.setText("Speed H / V: - / -");
                if (liveHeading != null) liveHeading.setText("Heading: -");
                if (liveIsGround != null) liveIsGround.setText("IsGround: -");
                if (liveUpdated != null) liveUpdated.setText("Updated: -");
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // drop references to avoid holding onto the Activity/view hierarchy
        textBasicInfo = textAirline = textRoute = textProgress = textAlt = textSpeed = textReg = null;

        depAirport = depCodes = depTerminal = depBaggage = depTimes = depRunways = null;
        arrAirport = arrCodes = arrTerminal = arrBaggage = arrTimes = arrRunways = null;

        flightNumber = flightCodeshared = airlineName = null;
        aircraftReg = aircraftCodes = aircraftModel = null;
        liveLatlon = liveAlt = liveSpeed = liveHeading = liveIsGround = liveUpdated = null;
    }
}
