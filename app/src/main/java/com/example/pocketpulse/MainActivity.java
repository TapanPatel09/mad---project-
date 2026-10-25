package com.example.pocketpulse;

import android.app.AlertDialog;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends android.app.Activity {
    private static final String PREFS_NAME = "pocketpulse_prefs";
    private static final String KEY_BUDGET_PAISE = "daily_budget_paise";
    private static final String KEY_EXPENSES = "expenses_json";
    private static final long DEFAULT_BUDGET_PAISE = 50000L;
    private static final String[] CATEGORIES = {
            "Food", "Transport", "Education", "Shopping", "Entertainment", "Other"
    };

    private SharedPreferences preferences;
    private final List<Expense> expenses = new ArrayList<>();

    private TextView todayDateText;
    private TextView budgetLimitText;
    private TextView spentTodayText;
    private TextView remainingText;
    private TextView budgetWarningText;
    private TextView emptyStateText;
    private ProgressBar budgetProgress;
    private LinearLayout categorySummaryContainer;
    private LinearLayout transactionsContainer;

    private long dailyBudgetPaise = DEFAULT_BUDGET_PAISE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        bindViews();
        loadBudget();
        loadExpenses();
        refreshDashboard();
    }

    private void bindViews() {
        todayDateText = findViewById(R.id.todayDateText);
        budgetLimitText = findViewById(R.id.budgetLimitText);
        spentTodayText = findViewById(R.id.spentTodayText);
        remainingText = findViewById(R.id.remainingText);
        budgetWarningText = findViewById(R.id.budgetWarningText);
        emptyStateText = findViewById(R.id.emptyStateText);
        budgetProgress = findViewById(R.id.budgetProgress);
        categorySummaryContainer = findViewById(R.id.categorySummaryContainer);
        transactionsContainer = findViewById(R.id.transactionsContainer);

        findViewById(R.id.addExpenseButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAddExpenseDialog();
            }
        });

        findViewById(R.id.setBudgetButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBudgetDialog();
            }
        });
    }

    private void loadBudget() {
        dailyBudgetPaise = preferences.getLong(KEY_BUDGET_PAISE, DEFAULT_BUDGET_PAISE);
        if (dailyBudgetPaise <= 0) {
            dailyBudgetPaise = DEFAULT_BUDGET_PAISE;
            preferences.edit().putLong(KEY_BUDGET_PAISE, dailyBudgetPaise).apply();
        }
    }

    private void loadExpenses() {
        expenses.clear();
        String storedJson = preferences.getString(KEY_EXPENSES, "[]");
        try {
            JSONArray array = new JSONArray(storedJson == null ? "[]" : storedJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = object.optString("id", "");
                String title = object.optString("title", "").trim();
                long amountPaise = object.optLong("amountPaise", -1L);
                String category = object.optString("category", "Other");
                String note = object.optString("note", "");
                long timestamp = object.optLong("timestamp", 0L);
                if (!id.isEmpty() && !title.isEmpty() && amountPaise > 0 && timestamp > 0) {
                    expenses.add(new Expense(id, title, amountPaise, normalizeCategory(category), note, timestamp));
                }
            }
        } catch (JSONException exception) {
            expenses.clear();
            preferences.edit().putString(KEY_EXPENSES, "[]").apply();
            Toast.makeText(this, "Saved expenses were reset because the data was unreadable.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveExpenses() {
        JSONArray array = new JSONArray();
        for (Expense expense : expenses) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", expense.id);
                object.put("title", expense.title);
                object.put("amountPaise", expense.amountPaise);
                object.put("category", expense.category);
                object.put("note", expense.note);
                object.put("timestamp", expense.timestamp);
                array.put(object);
            } catch (JSONException ignored) {
                // JSON objects created in memory should not fail for these primitive values.
            }
        }
        preferences.edit().putString(KEY_EXPENSES, array.toString()).apply();
    }

    private void refreshDashboard() {
        Collections.sort(expenses, new Comparator<Expense>() {
            @Override
            public int compare(Expense first, Expense second) {
                return Long.compare(second.timestamp, first.timestamp);
            }
        });

        todayDateText.setText(new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime()));

        long spentToday = getTodayTotalPaise();
        long remainingPaise = Math.max(dailyBudgetPaise - spentToday, 0L);
        int progress = dailyBudgetPaise > 0 ? (int) Math.min(100L, (spentToday * 100L) / dailyBudgetPaise) : 0;

        budgetLimitText.setText("Daily spending limit: " + formatMoney(dailyBudgetPaise));
        spentTodayText.setText(formatMoney(spentToday));
        remainingText.setText(formatMoney(remainingPaise));
        budgetProgress.setProgress(progress);
        updateBudgetWarning(spentToday);
        renderCategorySummary();
        renderTransactions();
    }

    private long getTodayTotalPaise() {
        long total = 0L;
        for (Expense expense : expenses) {
            if (isToday(expense.timestamp)) {
                total += expense.amountPaise;
            }
        }
        return total;
    }

    private void updateBudgetWarning(long spentToday) {
        if (spentToday > dailyBudgetPaise) {
            long overspent = spentToday - dailyBudgetPaise;
            budgetWarningText.setText("Budget exceeded by " + formatMoney(overspent) + ".");
            budgetWarningText.setTextColor(getColorCompat(R.color.danger));
        } else if (spentToday == dailyBudgetPaise) {
            budgetWarningText.setText("Daily budget reached!");
            budgetWarningText.setTextColor(getColorCompat(R.color.danger));
        } else if (spentToday * 100L >= dailyBudgetPaise * 80L) {
            budgetWarningText.setText("Careful: you have used at least 80% of today's budget.");
            budgetWarningText.setTextColor(getColorCompat(R.color.warning));
        } else {
            budgetWarningText.setText("You are within today's budget.");
            budgetWarningText.setTextColor(getColorCompat(R.color.text_muted));
        }
    }

    private void renderCategorySummary() {
        categorySummaryContainer.removeAllViews();
        Map<String, Long> totals = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            totals.put(category, 0L);
        }
        for (Expense expense : expenses) {
            if (isToday(expense.timestamp)) {
                totals.put(expense.category, totals.get(expense.category) + expense.amountPaise);
            }
        }
        for (String category : CATEGORIES) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            TextView nameText = new TextView(this);
            nameText.setText(category);
            nameText.setTextColor(getColorCompat(R.color.text_dark));
            nameText.setTextSize(15);
            row.addView(nameText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView amountText = new TextView(this);
            amountText.setText(formatMoney(totals.get(category)));
            amountText.setTextColor(getColorCompat(R.color.navy));
            amountText.setTextSize(15);
            amountText.setGravity(Gravity.END);
            amountText.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(amountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            categorySummaryContainer.addView(row);
        }
    }

    private void renderTransactions() {
        transactionsContainer.removeAllViews();
        emptyStateText.setVisibility(expenses.isEmpty() ? View.VISIBLE : View.GONE);

        for (final Expense expense : expenses) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(12), 0, dp(12));

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView titleText = new TextView(this);
            titleText.setText(expense.title);
            titleText.setTextColor(getColorCompat(R.color.text_dark));
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(titleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView amountText = new TextView(this);
            amountText.setText(formatMoney(expense.amountPaise));
            amountText.setTextColor(getColorCompat(R.color.danger));
            amountText.setTextSize(16);
            amountText.setGravity(Gravity.END);
            amountText.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(amountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            item.addView(topRow);

            TextView metaText = new TextView(this);
            metaText.setText(expense.category + " • " + formatDateTime(expense.timestamp));
            metaText.setTextColor(getColorCompat(R.color.text_muted));
            metaText.setTextSize(13);
            metaText.setPadding(0, dp(4), 0, 0);
            item.addView(metaText);

            if (!expense.note.trim().isEmpty()) {
                TextView noteText = new TextView(this);
                noteText.setText(expense.note.trim());
                noteText.setTextColor(getColorCompat(R.color.text_muted));
                noteText.setTextSize(13);
                noteText.setPadding(0, dp(3), 0, 0);
                item.addView(noteText);
            }

            Button deleteButton = new Button(this);
            deleteButton.setText("Delete");
            deleteButton.setTextColor(getColorCompat(R.color.danger));
            deleteButton.setAllCaps(false);
            deleteButton.setBackgroundResource(R.drawable.delete_button_background);
            deleteButton.setMinWidth(0);
            deleteButton.setPadding(dp(12), 0, dp(12), 0);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            deleteParams.topMargin = dp(8);
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete(expense);
                }
            });
            item.addView(deleteButton);

            transactionsContainer.addView(item);
        }
    }

    private void showAddExpenseDialog() {
        final EditText titleInput = createDialogEditText("Expense title", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final EditText amountInput = createDialogEditText("Amount in rupees", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final Spinner categorySpinner = new Spinner(this);
        final EditText noteInput = createDialogEditText("Optional note", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        form.setPadding(padding, dp(8), padding, 0);
        form.addView(titleInput, matchWrapParams(0));
        form.addView(amountInput, matchWrapParams(10));
        form.addView(categorySpinner, matchWrapParams(10));
        form.addView(noteInput, matchWrapParams(10));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String title = titleInput.getText().toString().trim();
                        Long amountPaise = parseRupeesToPaise(amountInput.getText().toString());
                        if (title.isEmpty()) {
                            titleInput.setError("Enter an expense title.");
                            titleInput.requestFocus();
                            return;
                        }
                        if (amountPaise == null || amountPaise <= 0L) {
                            amountInput.setError("Enter a valid amount greater than zero.");
                            amountInput.requestFocus();
                            return;
                        }
                        String category = categorySpinner.getSelectedItem() == null
                                ? "Other"
                                : categorySpinner.getSelectedItem().toString();
                        String note = noteInput.getText().toString().trim();
                        expenses.add(new Expense(
                                String.valueOf(System.currentTimeMillis()),
                                title,
                                amountPaise,
                                normalizeCategory(category),
                                note,
                                System.currentTimeMillis()
                        ));
                        saveExpenses();
                        refreshDashboard();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showBudgetDialog() {
        final EditText budgetInput = createDialogEditText("Daily budget in rupees", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        budgetInput.setText(formatPlainRupees(dailyBudgetPaise));
        budgetInput.setSelectAllOnFocus(true);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(18), dp(8), dp(18), 0);
        wrapper.addView(budgetInput, matchWrapParams(0));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set Daily Budget")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface dialogInterface) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Long budgetPaise = parseRupeesToPaise(budgetInput.getText().toString());
                        if (budgetPaise == null || budgetPaise <= 0L) {
                            budgetInput.setError("Enter a valid budget greater than zero.");
                            budgetInput.requestFocus();
                            return;
                        }
                        dailyBudgetPaise = budgetPaise;
                        preferences.edit().putLong(KEY_BUDGET_PAISE, dailyBudgetPaise).apply();
                        refreshDashboard();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }

    private void confirmDelete(final Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Expense")
                .setMessage("Delete \"" + expense.title + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialogInterface, int which) {
                        expenses.remove(expense);
                        saveExpenses();
                        refreshDashboard();
                    }
                })
                .show();
    }

    private EditText createDialogEditText(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        editText.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        editText.setBackgroundResource(R.drawable.edit_text_background);
        editText.setTextColor(getColorCompat(R.color.text_dark));
        editText.setHintTextColor(getColorCompat(R.color.text_muted));
        return editText;
    }

    private LinearLayout.LayoutParams matchWrapParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private Long parseRupeesToPaise(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().replace(",", "");
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            BigDecimal rupees = new BigDecimal(normalized);
            if (rupees.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            BigDecimal paise = rupees.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);
            if (paise.compareTo(new BigDecimal(Long.MAX_VALUE)) > 0) {
                return null;
            }
            return paise.longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private boolean isToday(long timestamp) {
        Calendar expenseCalendar = Calendar.getInstance();
        expenseCalendar.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        return expenseCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && expenseCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return "Other";
        }
        List<String> validCategories = Arrays.asList(CATEGORIES);
        return validCategories.contains(category) ? category : "Other";
    }

    private String formatMoney(long paise) {
        BigDecimal rupees = new BigDecimal(paise).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return "\u20B9" + rupees.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPlainRupees(long paise) {
        BigDecimal rupees = new BigDecimal(paise).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return rupees.stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(long timestamp) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(timestamp);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getColorCompat(int colorResId) {
        return getColor(colorResId);
    }

    private static class Expense {
        final String id;
        final String title;
        final long amountPaise;
        final String category;
        final String note;
        final long timestamp;

        Expense(String id, String title, long amountPaise, String category, String note, long timestamp) {
            this.id = id;
            this.title = title;
            this.amountPaise = amountPaise;
            this.category = category;
            this.note = note == null ? "" : note;
            this.timestamp = timestamp;
        }
    }
}
