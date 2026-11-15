package ru.labhistory.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.R;
import ru.labhistory.app.data.IndicatorMeasurement;
import ru.labhistory.app.parser.ReferenceRangeEvaluator;

public class HistoryChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outOfRangePointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<PointValue> points = new ArrayList<>();
    private final DecimalFormat valueFormat = new DecimalFormat("0.###");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "dd.MM.yy",
            Locale.forLanguageTag("ru-RU")
    );

    public HistoryChartView(Context context) {
        this(context, null);
    }

    public HistoryChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.chart_grid));
        gridPaint.setStrokeWidth(dp(1));
        linePaint.setColor(ContextCompat.getColor(context, R.color.primary));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        pointPaint.setColor(ContextCompat.getColor(context, R.color.accent));
        pointPaint.setStyle(Paint.Style.FILL);
        outOfRangePointPaint.setColor(ContextCompat.getColor(context, R.color.danger));
        outOfRangePointPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        textPaint.setTextSize(sp(11));
    }

    public void setMeasurements(List<IndicatorMeasurement> measurements) {
        points.clear();
        int outOfRangeCount = 0;
        if (measurements != null) {
            for (IndicatorMeasurement measurement : measurements) {
                try {
                    ReferenceRangeEvaluator.Status status = ReferenceRangeEvaluator.evaluate(
                            measurement.numericValue,
                            measurement.referenceLow,
                            measurement.referenceHigh
                    );
                    boolean outOfRange = status == ReferenceRangeEvaluator.Status.BELOW
                            || status == ReferenceRangeEvaluator.Status.ABOVE;
                    points.add(new PointValue(
                            measurement.analysisDate,
                            Double.parseDouble(measurement.numericValue),
                            outOfRange
                    ));
                    if (outOfRange) {
                        outOfRangeCount++;
                    }
                } catch (Exception ignored) {
                    // Non-numeric rows are intentionally omitted from the chart.
                }
            }
        }
        setContentDescription(
                "График по " + points.size() + " измерениям; вне референса: " + outOfRangeCount
        );
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) {
            return;
        }

        float left = dp(58);
        float right = getWidth() - dp(16);
        float top = dp(22);
        float bottom = getHeight() - dp(42);
        if (right <= left || bottom <= top) {
            return;
        }

        double minValue = points.get(0).value;
        double maxValue = points.get(0).value;
        long minDate = points.get(0).date;
        long maxDate = points.get(points.size() - 1).date;
        for (PointValue point : points) {
            minValue = Math.min(minValue, point.value);
            maxValue = Math.max(maxValue, point.value);
            minDate = Math.min(minDate, point.date);
            maxDate = Math.max(maxDate, point.date);
        }

        double valuePadding = maxValue == minValue
                ? Math.max(Math.abs(maxValue) * 0.1, 1.0)
                : (maxValue - minValue) * 0.12;
        double graphMin = minValue - valuePadding;
        double graphMax = maxValue + valuePadding;

        for (int step = 0; step <= 4; step++) {
            float y = top + (bottom - top) * step / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            double labelValue = graphMax - (graphMax - graphMin) * step / 4d;
            canvas.drawText(valueFormat.format(labelValue), dp(4), y + sp(4), textPaint);
        }

        Path path = new Path();
        for (int index = 0; index < points.size(); index++) {
            PointValue point = points.get(index);
            float x;
            if (maxDate == minDate) {
                x = points.size() == 1
                        ? (left + right) / 2f
                        : left + (right - left) * index / (points.size() - 1f);
            } else {
                x = left + (right - left) * (point.date - minDate) / (float) (maxDate - minDate);
            }
            float y = (float) (bottom - (bottom - top)
                    * (point.value - graphMin) / (graphMax - graphMin));
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, linePaint);

        for (int index = 0; index < points.size(); index++) {
            PointValue point = points.get(index);
            float x;
            if (maxDate == minDate) {
                x = points.size() == 1
                        ? (left + right) / 2f
                        : left + (right - left) * index / (points.size() - 1f);
            } else {
                x = left + (right - left) * (point.date - minDate) / (float) (maxDate - minDate);
            }
            float y = (float) (bottom - (bottom - top)
                    * (point.value - graphMin) / (graphMax - graphMin));
            canvas.drawCircle(
                    x,
                    y,
                    dp(5),
                    point.outOfRange ? outOfRangePointPaint : pointPaint
            );
        }

        String firstDate = dateFormat.format(new Date(minDate));
        String lastDate = dateFormat.format(new Date(maxDate));
        canvas.drawText(firstDate, left, getHeight() - dp(14), textPaint);
        if (maxDate != minDate) {
            float width = textPaint.measureText(lastDate);
            canvas.drawText(lastDate, right - width, getHeight() - dp(14), textPaint);
        }
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class PointValue {
        final long date;
        final double value;
        final boolean outOfRange;

        PointValue(long date, double value, boolean outOfRange) {
            this.date = date;
            this.value = value;
            this.outOfRange = outOfRange;
        }
    }
}
