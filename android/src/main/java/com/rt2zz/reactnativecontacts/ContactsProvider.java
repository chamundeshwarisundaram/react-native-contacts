package com.rt2zz.reactnativecontacts;

import android.content.ContentResolver;
import android.database.Cursor;
import android.icu.text.Collator;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;

import static android.provider.ContactsContract.CommonDataKinds.Contactables;
import static android.provider.ContactsContract.CommonDataKinds.Email;
import static android.provider.ContactsContract.CommonDataKinds.Event;
import static android.provider.ContactsContract.CommonDataKinds.Organization;
import static android.provider.ContactsContract.CommonDataKinds.Phone;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.CommonDataKinds.Note;
import static android.provider.ContactsContract.CommonDataKinds.Website;
import static android.provider.ContactsContract.CommonDataKinds.StructuredPostal;

public class ContactsProvider {
    public static final int ID_FOR_PROFILE_CONTACT = -1;

    private static final List<String> JUST_ME_PROJECTION = new ArrayList<String>() {{
        add((ContactsContract.Data._ID));
        add(ContactsContract.Data.CONTACT_ID);
        add(ContactsContract.Data.RAW_CONTACT_ID);
        add(ContactsContract.Data.LOOKUP_KEY);
        add(ContactsContract.Contacts.Data.MIMETYPE);
        add(ContactsContract.Profile.DISPLAY_NAME);
        add(Contactables.PHOTO_URI);
        add(StructuredName.DISPLAY_NAME);
        add(StructuredName.GIVEN_NAME);
        add(StructuredName.PHONETIC_GIVEN_NAME);
        add(StructuredName.MIDDLE_NAME);
        add(StructuredName.FAMILY_NAME);
        add(StructuredName.PHONETIC_FAMILY_NAME);
        add(StructuredName.PHONETIC_NAME_STYLE);
        add(StructuredName.PHONETIC_NAME);
        add(StructuredName.PREFIX);
        add(StructuredName.SUFFIX);
        add(Phone.NUMBER);
        add(Phone.NORMALIZED_NUMBER);
        add(Phone.TYPE);
        add(Phone.LABEL);
        add(Email.DATA);
        add(Email.ADDRESS);
        add(Email.TYPE);
        add(Email.LABEL);
        add(Organization.COMPANY);
        add(Organization.TITLE);
        add(Organization.DEPARTMENT);
        add(StructuredPostal.FORMATTED_ADDRESS);
        add(StructuredPostal.TYPE);
        add(StructuredPostal.LABEL);
        add(StructuredPostal.STREET);
        add(StructuredPostal.POBOX);
        add(StructuredPostal.NEIGHBORHOOD);
        add(StructuredPostal.CITY);
        add(StructuredPostal.REGION);
        add(StructuredPostal.POSTCODE);
        add(StructuredPostal.COUNTRY);
        add(Note.NOTE);
        add(Website.URL);
        add(Event.START_DATE);
        add(Event.TYPE);
    }};

    private static final List<String> FULL_PROJECTION = new ArrayList<String>() {{
        addAll(JUST_ME_PROJECTION);
    }};

    private static final List<String> PHOTO_PROJECTION = new ArrayList<String>() {{
        add(Contactables.PHOTO_URI);
    }};

    private final ContentResolver contentResolver;

    public ContactsProvider(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public WritableArray getContactsMatchingString(String searchString,Context context) {
        Map<String, Contact> matchingContacts;
        {
            Cursor cursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    FULL_PROJECTION.toArray(new String[FULL_PROJECTION.size()]),
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? OR " +
                            Organization.COMPANY + " LIKE ?",
                    new String[]{"%" + searchString + "%", "%" + searchString + "%"},
                    null
            );

            try {
                matchingContacts = loadContactsFrom(cursor,context,true);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        WritableArray contacts = Arguments.createArray();
        for (Contact contact : matchingContacts.values()) {
            contacts.pushMap(contact.toMap());
        }
        return contacts;
    }

    public WritableMap getContactByRawId(String contactRawId) {

        // Get Contact Id from Raw Contact Id
        String[] projections = new String[]{ContactsContract.RawContacts.CONTACT_ID};
        String select = ContactsContract.RawContacts._ID + "= ?";
        String[] selectionArgs = new String[]{contactRawId};
        Cursor rawCursor = contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, projections, select, selectionArgs, null);
        String contactId = null;
        if (rawCursor.getCount() == 0) {
            /*contact id not found */
        }

        if (rawCursor.moveToNext()) {
            int columnIndex;
            columnIndex = rawCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID);
            if (columnIndex == -1) {
                /* trouble getting contact id */
            } else {
                contactId = rawCursor.getString(columnIndex);
            }
        }

        rawCursor.close();

        //Now that we have the real contact id, fetch information
        return getContactById(contactId);
    }

    public WritableMap getContactById(String contactId) {

        Map<String, Contact> matchingContacts;
        {
            Cursor cursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    FULL_PROJECTION.toArray(new String[FULL_PROJECTION.size()]),
                    ContactsContract.RawContacts.CONTACT_ID + " = ?",
                    new String[]{contactId},
                    null
            );

            try {
                matchingContacts = loadContactsFrom(cursor,null,true);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        if(matchingContacts.values().size() > 0) {
            return matchingContacts.values().iterator().next().toMap();
        }

        return null;
    }

    public WritableArray getContacts(boolean isLastNameSortOrder,Context context) {
        Map<String, Contact> justMe;
        {
            Cursor cursor = contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
                    JUST_ME_PROJECTION.toArray(new String[JUST_ME_PROJECTION.size()]),
                    null,
                    null,
                    null
            );

            try {
                justMe = loadContactsFrom(cursor,context,isLastNameSortOrder);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        Map<String, Contact> everyoneElse;
        {
            Cursor cursor = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    FULL_PROJECTION.toArray(new String[FULL_PROJECTION.size()]),
                    ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=? OR "
                            + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{
                            Email.CONTENT_ITEM_TYPE,
                            Phone.CONTENT_ITEM_TYPE,
                            StructuredName.CONTENT_ITEM_TYPE,
                            Organization.CONTENT_ITEM_TYPE,
                            StructuredPostal.CONTENT_ITEM_TYPE,
                            Note.CONTENT_ITEM_TYPE,
                            Website.CONTENT_ITEM_TYPE,
                            Event.CONTENT_ITEM_TYPE,
                    },
                    null
            );

            try {
                everyoneElse = loadContactsFrom(cursor,context,isLastNameSortOrder);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        WritableArray contacts = Arguments.createArray();
        for (Contact contact : justMe.values()) {
            contacts.pushMap(contact.toMap());
        }
        for (Contact contact : everyoneElse.values()) {
            contacts.pushMap(contact.toMap());
        }

        return contacts;
    }

    @NonNull
    private Map<String, Contact> loadContactsFrom(Cursor cursor,Context context,final boolean isLastNameSortOrder) {

        Map<String, Contact> map = new LinkedHashMap<>();

        while (cursor != null && cursor.moveToNext()) {

            int columnIndexContactId = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID);
            int columnIndexId = cursor.getColumnIndex(ContactsContract.Data._ID);
            int columnIndexRawContactId = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID);
            String contactId;
            String id;
            String rawContactId;
            if (columnIndexContactId != -1) {
                contactId = cursor.getString(columnIndexContactId);
            } else {
                //todo - double check this, it may not be necessary any more
                contactId = String.valueOf(ID_FOR_PROFILE_CONTACT);//no contact id for 'ME' user
            }

            if (columnIndexId != -1) {
                id = cursor.getString(columnIndexId);
            } else {
                //todo - double check this, it may not be necessary any more
                id = String.valueOf(ID_FOR_PROFILE_CONTACT);//no contact id for 'ME' user
            }

            if (columnIndexRawContactId != -1) {
                rawContactId = cursor.getString(columnIndexRawContactId);
            } else {
                //todo - double check this, it may not be necessary any more
                rawContactId = String.valueOf(ID_FOR_PROFILE_CONTACT);//no contact id for 'ME' user
            }

            if (!map.containsKey(contactId)) {
                map.put(contactId, new Contact(contactId));
            }

            Contact contact = map.get(contactId);
            String mimeType = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.MIMETYPE));
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            contact.rawContactId = rawContactId;
            if (!TextUtils.isEmpty(name) && TextUtils.isEmpty(contact.displayName)) {
                contact.displayName = name;
            }

            if (TextUtils.isEmpty(contact.photoUri)) {
                String rawPhotoURI = cursor.getString(cursor.getColumnIndex(Contactables.PHOTO_URI));
                if (!TextUtils.isEmpty(rawPhotoURI)) {
                    contact.photoUri = rawPhotoURI;
                    contact.hasPhoto = true;
                }
            }

            switch(mimeType) {
                case StructuredName.CONTENT_ITEM_TYPE:
                    contact.givenName = cursor.getString(cursor.getColumnIndex(StructuredName.GIVEN_NAME));
                    contact.phoneticGivenName = cursor.getString(cursor.getColumnIndex(StructuredName.PHONETIC_GIVEN_NAME));
                    contact.middleName = cursor.getString(cursor.getColumnIndex(StructuredName.MIDDLE_NAME));
                    contact.familyName = cursor.getString(cursor.getColumnIndex(StructuredName.FAMILY_NAME));
                    contact.phoneticFamilyName= cursor.getString(cursor.getColumnIndex(StructuredName.PHONETIC_FAMILY_NAME));
                    contact.prefix = cursor.getString(cursor.getColumnIndex(StructuredName.PREFIX));
                    contact.suffix = cursor.getString(cursor.getColumnIndex(StructuredName.SUFFIX));
                    contact.phoneticNameStyle= cursor.getString(cursor.getColumnIndex(StructuredName.PHONETIC_NAME_STYLE));
                    contact.phoneticName= cursor.getString(cursor.getColumnIndex(StructuredName.PHONETIC_NAME));

                    //Fixed crash issue.
                    //Fixed : Contact not displayed if the phoneticFamilyname or PhoneticGivenName exist and if phoneticNameStyle not 4.
                    if( (!TextUtils.isEmpty(contact.phoneticFamilyName) || !TextUtils.isEmpty(contact.phoneticGivenName) ) || (contact.phoneticNameStyle != null && contact.phoneticNameStyle.equalsIgnoreCase("4"))) {
                        //JAPANESE Phonetic style..
                        if (!TextUtils.isEmpty(contact.phoneticFamilyName)) {

                            contact.katakanaFamilyName = hiragana2Katakana(contact.phoneticFamilyName);
                            //contact.katakanaFamilyName = convertToHiragana(contact.phoneticFamilyName);
                        }
                        if (!TextUtils.isEmpty(contact.phoneticGivenName)) {
                            contact.katakanaGivenName = hiragana2Katakana(contact.phoneticGivenName);
                            //contact.phoneticGivenName = convertToHiragana(contact.phoneticGivenName);
                        }
                    }


                    //Keeping the default name
                    String sortedString = "#";

                    if (isLastNameSortOrder) {
                        if(!TextUtils.isEmpty(contact.katakanaFamilyName)){
                            sortedString=contact.katakanaFamilyName;
                        }else  if(!TextUtils.isEmpty(contact.familyName)){
                            if(!contact.phoneticNameStyle.equalsIgnoreCase("4") || ! isChineseChar(contact.familyName)) {
                                sortedString = contact.familyName;
                            }
                        }else if(!TextUtils.isEmpty(contact.katakanaGivenName)){
                           sortedString= contact.katakanaGivenName;
                        }else if(!TextUtils.isEmpty(contact.givenName)){
                            if(!contact.phoneticNameStyle.equalsIgnoreCase("4") || ! isChineseChar(contact.familyName)) {
                                sortedString = contact.givenName;
                            }
                        }

                    } else {

                        if(!TextUtils.isEmpty(contact.katakanaGivenName)){
                            sortedString= contact.katakanaGivenName;
                        }else if(!TextUtils.isEmpty(contact.givenName)){
                            if(!contact.phoneticNameStyle.equalsIgnoreCase("4") || ! isChineseChar(contact.familyName)) {
                                sortedString = contact.givenName;
                            }
                        }else if(!TextUtils.isEmpty(contact.katakanaFamilyName)){
                            sortedString=contact.katakanaFamilyName;
                        }else  if(!TextUtils.isEmpty(contact.familyName)){
                            if(!contact.phoneticNameStyle.equalsIgnoreCase("4") || ! isChineseChar(contact.familyName)) {
                                sortedString = contact.familyName;
                            }
                        }
                    }

                    contact.sortGroup=String.valueOf(getSortGroup(sortedString));
                    contact.sortName=sortedString;

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("sortName: ").append(contact.sortName).append(" , ")
                            .append("phoneticFamilyName: ").append(contact.phoneticFamilyName).append(" , ")
                            .append("katakanaFamilyName: ").append(contact.katakanaFamilyName).append(" , ")
                            .append("phoneticGivenName: ").append(contact.phoneticGivenName).append(" , ")
                            .append("katakanaGivenName: ").append(contact.katakanaGivenName).append(" , ")
                            .append("sortGroup: ").append(contact.sortGroup).append(" , ")
                            .append("givenName: ").append(contact.givenName).append(" , ")
                            .append("middleName: ").append(contact.middleName).append(" , ")
                            .append("familyName: ").append(contact.familyName);


                    //Log.d("ContactsProvider", "Contacts: " +stringBuilder.toString());
                    //Log.d("ContactsProvider", "ContactssortGroup  " + contact.sortGroup +" , sortedString: "+sortedString);

                    break;
                case Phone.CONTENT_ITEM_TYPE:
                    String phoneNumber = cursor.getString(cursor.getColumnIndex(Phone.NUMBER));
                    int phoneType = cursor.getInt(cursor.getColumnIndex(Phone.TYPE));

                    if (!TextUtils.isEmpty(phoneNumber)) {
                        String label;
                        switch (phoneType) {
                            case Phone.TYPE_HOME:
                                if(context !=null){
                                    label=ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(), phoneType , "").toString();
                                }else{
                                label = "home";
                                }
                                break;
                            case Phone.TYPE_WORK:
                                if(context !=null){
                                    label=ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(), phoneType , "").toString();
                                }else{
                                label = "work";
                                }
                                break;
                            case Phone.TYPE_MOBILE:
                                if(context !=null){
                                    label=ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(), phoneType , "").toString();
                                }else{
                                label = "mobile";
                                }
                                break;
                            default:
                                int customIndex = cursor.getColumnIndex(Phone.TYPE);
                                int labelType = cursor.getInt(customIndex);
                                if (context != null) {
                                    if (labelType == Phone.TYPE_CUSTOM) {
                                        label = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL));
                                    } else {
                                        label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(), phoneType, "").toString();
                                    }
                                } else {
                                    label = "other";
                                }
                        }
                        contact.phones.add(new Contact.Item(label, phoneNumber, id));

                    }
                    break;
                case Email.CONTENT_ITEM_TYPE:
                    String email = cursor.getString(cursor.getColumnIndex(Email.ADDRESS));
                    int emailType = cursor.getInt(cursor.getColumnIndex(Email.TYPE));
                    if (!TextUtils.isEmpty(email)) {
                        String label;
                        switch (emailType) {
                            case Email.TYPE_HOME:
                                label = "home";
                                break;
                            case Email.TYPE_WORK:
                                label = "work";
                                break;
                            case Email.TYPE_MOBILE:
                                label = "mobile";
                                break;
                            case Email.TYPE_CUSTOM:
                                if (cursor.getString(cursor.getColumnIndex(Email.LABEL)) != null) {
                                    label = cursor.getString(cursor.getColumnIndex(Email.LABEL)).toLowerCase();
                                } else {
                                    label = "";
                                }
                                break;
                            default:
                                label = "other";
                        }
                        contact.emails.add(new Contact.Item(label, email, id));
                    }
                    break;
                case Organization.CONTENT_ITEM_TYPE:
                    contact.company = cursor.getString(cursor.getColumnIndex(Organization.COMPANY));
                    contact.jobTitle = cursor.getString(cursor.getColumnIndex(Organization.TITLE));
                    contact.department = cursor.getString(cursor.getColumnIndex(Organization.DEPARTMENT));
                    break;
                case StructuredPostal.CONTENT_ITEM_TYPE:
                    contact.postalAddresses.add(new Contact.PostalAddressItem(cursor));
                    break;
                case Event.CONTENT_ITEM_TYPE:
                    int eventType = cursor.getInt(cursor.getColumnIndex(Event.TYPE));
                    if (eventType == Event.TYPE_BIRTHDAY) {
                        try {
                            String birthday = cursor.getString(cursor.getColumnIndex(Event.START_DATE)).replace("--", "");
                            String[] yearMonthDay = birthday.split("-");
                            List<String> yearMonthDayList = Arrays.asList(yearMonthDay);

                            if (yearMonthDayList.size() == 2) {
                                // birthday is formatted "12-31"
                                int month = Integer.parseInt(yearMonthDayList.get(0));
                                int day = Integer.parseInt(yearMonthDayList.get(1));
                                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                                    contact.birthday = new Contact.Birthday(month, day);
                                }
                            } else if (yearMonthDayList.size() == 3) {
                                // birthday is formatted "1986-12-31"
                                int year = Integer.parseInt(yearMonthDayList.get(0));
                                int month = Integer.parseInt(yearMonthDayList.get(1));
                                int day = Integer.parseInt(yearMonthDayList.get(2));
                                if (year > 0 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                                    contact.birthday = new Contact.Birthday(year, month, day);
                                }
                            }
                        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                            // whoops, birthday isn't in the format we expect
                            Log.w("ContactsProvider", e.toString());

                        }
                    }
                    break;
            }
        }

        return map;
    }

    public String getPhotoUriFromContactId(String contactId) {
        Cursor cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                PHOTO_PROJECTION.toArray(new String[PHOTO_PROJECTION.size()]),
                ContactsContract.RawContacts.CONTACT_ID + " = ?",
                new String[]{contactId},
                null
        );
        try {
            if (cursor != null && cursor.moveToNext()) {
                String rawPhotoURI = cursor.getString(cursor.getColumnIndex(Contactables.PHOTO_URI));
                if (!TextUtils.isEmpty(rawPhotoURI)) {
                    return rawPhotoURI;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private boolean isChineseChar(String c){

        Pattern p = Pattern.compile("/[\u3000-\u303F]|[\u4E00-\u9FAF]|[\u2605-\u2606]|[\u2190-\u2195]|\u203B/g;");
        Matcher m= p.matcher(c);
        if(m.find()){
            return true;
        }
        return false;

    }


    public String hiragana2Katakana(String str) {

        int delta = 'ア' - 'あ'; //差分
        StringBuilder buf = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char code = str.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(code);
            if (block != null && block.equals(Character.UnicodeBlock.HIRAGANA)) {
                buf.append((char)(code + delta));
            } else {
                buf.append(code);
            }
        }
        return buf.toString();
    }

    private char getSortGroup(String name) {

        if (name == null || name.length() == 0) return '#';

        char c = name.charAt(0);
        if (isChineseChar(String.valueOf(c))) {
            //Log.d("Kana", "Converted from Chinese character found : " + c);
            return '#';
        }

        if (isKatakana(c)) {
            //Change to Hiragana.
            //Log.d("Kana", "Converted from Katakana: " + c);
            c = toHiragana(c);
            //Log.d("Kana", "Converted from Katakana to Hiragana: " + c);
        }else {
            //if the name start with number/special character/small letter it should display all time in # group.
            if(!Character.isLetter(c)){
                //Log.d("Kana", "returned group as #.  sortname: " + name);
                return '#';
            }else {
                if(Character.isAlphabetic(c)){
                    //Log.d("Kana", "returned group as uppercase.  sortname: " + name + " Character.toUpperCase(c): "+Character.toUpperCase(c));
                    return Character.toUpperCase(c);
                }
                return c;
            }
        }


        //reference: https://www.unicode.org/charts/PDF/U3040.pdf / http://japanese-lesson.com/resources/reference/alphabetical.html
        if (((c >= '\u3041') && (c <= '\u304a') )|| (c == '\u3094')) {
            //あ
            return (char) '\u3042';

        } else if ( ((c >= '\u304b') && (c <= '\u3054')) || (c == '\u3095') || (c == '\u3096')) {
            // か
            return (char) '\u304b';

        } else if ((c >= '\u3055') && (c <= '\u305e')) {
            //さ
            return (char) '\u3055';

        } else if ((c >= '\u305f') && (c <= '\u3069')) {
            //た
            return (char) '\u305f';

        } else if ((c >= '\u306a') && (c <= '\u306e')) {
            //な
            return (char) '\u306a';

        } else if ((c >= '\u306f') && (c <= '\u307d')) {
            //は
            return (char) '\u306f';

        } else if ((c >= '\u307e') && (c <= '\u3082')) {
            //ま
            return (char) '\u307e';

        } else if ((c >= '\u3083') && (c <= '\u3088')) {
            //や
            return (char) '\u3084';

        } else if ((c >= '\u3089') && (c <= '\u308d')) {
            //ら
            return (char) '\u3089';

        } else if ((c >= '\u308e') && (c <= '\u3093')) {
            //わ
            return (char) '\u308f';
        }

        //return default group as '#'
        return '#';


    }


    private static class Contact {
        private String contactId;
        private String rawContactId;
        private String displayName;
        private String givenName = "";
        private String phoneticGivenName = "";
        private String katakanaGivenName = "";
        private String middleName = "";
        private String familyName = "";
        private String phoneticFamilyName = "";
        private String katakanaFamilyName = "";
        private String prefix = "";
        private String suffix = "";
        private String company = "";
        private String jobTitle = "";
        private String department = "";
        private String note ="";
        private List<Item> urls = new ArrayList<>();
        private boolean hasPhoto = false;
        private String photoUri;
        private List<Item> emails = new ArrayList<>();
        private List<Item> phones = new ArrayList<>();
        private List<PostalAddressItem> postalAddresses = new ArrayList<>();
        private Birthday birthday;
        private String sortName = "";
        private String sortGroup = "";
        private String phoneticNameStyle = "";
        private String phoneticName = "";


        public Contact(String contactId) {
            this.contactId = contactId;
        }

        public WritableMap toMap() {
            WritableMap contact = Arguments.createMap();
            contact.putString("recordID", contactId);
            contact.putString("rawContactId", rawContactId);
            contact.putString("givenName", TextUtils.isEmpty(givenName) ? displayName : givenName);
            contact.putString("phoneticGivenName", phoneticGivenName);
            contact.putString("middleName", middleName);
            contact.putString("familyName", familyName);
            contact.putString("phoneticFamilyName", phoneticFamilyName);
            contact.putString("prefix", prefix);
            contact.putString("suffix", suffix);
            contact.putString("company", company);
            contact.putString("jobTitle", jobTitle);
            contact.putString("department", department);
            contact.putString("note", note);
            contact.putBoolean("hasThumbnail", this.hasPhoto);
            contact.putString("thumbnailPath", photoUri == null ? "" : photoUri);
            contact.putString("katakanaFamilyName",katakanaFamilyName);
            contact.putString("katakanaGivenName",katakanaGivenName);
            contact.putString("sortName",sortName);
            contact.putString("sortGroup",sortGroup);

            WritableArray phoneNumbers = Arguments.createArray();
            for (Item item : phones) {
                WritableMap map = Arguments.createMap();
                map.putString("number", item.value);
                map.putString("label", item.label);
                map.putString("id", item.id);
                phoneNumbers.pushMap(map);
            }
            contact.putArray("phoneNumbers", phoneNumbers);

            WritableArray urlAddresses = Arguments.createArray();
            for (Item item : urls) {
                WritableMap map = Arguments.createMap();
                map.putString("url", item.value);
                map.putString("id", item.id);
                urlAddresses.pushMap(map);
            }
            contact.putArray("urlAddresses", urlAddresses);

            WritableArray emailAddresses = Arguments.createArray();
            for (Item item : emails) {
                WritableMap map = Arguments.createMap();
                map.putString("email", item.value);
                map.putString("label", item.label);
                map.putString("id", item.id);
                emailAddresses.pushMap(map);
            }
            contact.putArray("emailAddresses", emailAddresses);

            WritableArray postalAddresses = Arguments.createArray();
            for (PostalAddressItem item : this.postalAddresses) {
                postalAddresses.pushMap(item.map);
            }
            contact.putArray("postalAddresses", postalAddresses);

            WritableMap birthdayMap = Arguments.createMap();
            if (birthday != null) {
                if (birthday.year > 0) {
                    birthdayMap.putInt("year", birthday.year);
                }
                birthdayMap.putInt("month", birthday.month);
                birthdayMap.putInt("day", birthday.day);
                contact.putMap("birthday", birthdayMap);
            }

            return contact;
        }

        public static class Item {
            public String label;
            public String value;
            public String id;

            public Item(String label, String value, String id) {
                this.id = id;
                this.label = label;
                this.value = value;
            }

            public Item(String label, String value) {
                this.label = label;
                this.value = value;
            }
        }

        public static class Birthday {
            public int year = 0;
            public int month = 0;
            public int day = 0;

            public Birthday(int year, int month, int day) {
                this.year = year;
                this.month = month;
                this.day = day;
            }

            public Birthday(int month, int day) {
                this.month = month;
                this.day = day;
            }
        }

        public static class PostalAddressItem {
            public final WritableMap map;

            public PostalAddressItem(Cursor cursor) {
                map = Arguments.createMap();

                map.putString("label", getLabel(cursor));
                putString(cursor, "formattedAddress", StructuredPostal.FORMATTED_ADDRESS);
                putString(cursor, "street", StructuredPostal.STREET);
                putString(cursor, "pobox", StructuredPostal.POBOX);
                putString(cursor, "neighborhood", StructuredPostal.NEIGHBORHOOD);
                putString(cursor, "city", StructuredPostal.CITY);
                putString(cursor, "region", StructuredPostal.REGION);
                putString(cursor, "state", StructuredPostal.REGION);
                putString(cursor, "postCode", StructuredPostal.POSTCODE);
                putString(cursor, "country", StructuredPostal.COUNTRY);
            }

            private void putString(Cursor cursor, String key, String androidKey) {
                final String value = cursor.getString(cursor.getColumnIndex(androidKey));
                if (!TextUtils.isEmpty(value))
                    map.putString(key, value);
            }

            static String getLabel(Cursor cursor) {
                switch (cursor.getInt(cursor.getColumnIndex(StructuredPostal.TYPE))) {
                    case StructuredPostal.TYPE_HOME:
                        return "home";
                    case StructuredPostal.TYPE_WORK:
                        return "work";
                    case StructuredPostal.TYPE_CUSTOM:
                        final String label = cursor.getString(cursor.getColumnIndex(StructuredPostal.LABEL));
                        return label != null ? label : "";
                }
                return "other";
            }
        }
    }

    public String convertKana(String input) {
        if (input == null || input.length() == 0) return "";

        StringBuilder out = new StringBuilder();
        char ch = input.charAt(0);

        if (isHiragana(ch)) { // convert to hiragana to katakana
            for (int i = 0; i < input.length(); i++) {
                out.append(toKatakana(input.charAt(i)));
            }
       } else { // do nothing if neither
            return input;
        }
        //else if (isKatakana(ch)) { // convert to katakana to hiragana
//            for (int i = 0; i < input.length(); i++) {
//                out.append(toHiragana(input.charAt(i)));
//            }
//        }
//        else { // do nothing if neither
//            return input;
//        }

        return out.toString();
    }

    public String convertToHiragana(String input) {
        if (input == null || input.length() == 0) return "";

        StringBuilder out = new StringBuilder();
        char ch = input.charAt(0);
       if (isKatakana(ch)) { // convert to katakana to hiragana
            for (int i = 0; i < input.length(); i++) {
                out.append(toHiragana(input.charAt(i)));
            }
        }
        else { // do nothing if neither
            return input;
        }

        return out.toString();
    }

    /**
     * Determines if this character is a Japanese Kana.
     */
    public  boolean isKana(char c) {
        return (isHiragana(c) || isKatakana(c));
    }

    /**
     * Determines if this character is one of the Japanese Hiragana.
     */
    public  boolean isHiragana(char c) {
        return (('\u3041' <= c) && (c <= '\u309e'));
    }

    /**
     * Determines if this character is one of the Japanese Katakana.
     */
    public  boolean isKatakana(char c) {
        return (isHalfWidthKatakana(c) || isFullWidthKatakana(c));
    }

    /**
     * Determines if this character is a Half width Katakana.
     */
    public  boolean isHalfWidthKatakana(char c) {
        return (('\uff66' <= c) && (c <= '\uff9d'));
    }

    /**
     * Determines if this character is a Full width Katakana.
     */
    public  boolean isFullWidthKatakana(char c) {
        return (('\u30a1' <= c) && (c <= '\u30fe'));
    }

    /**
     * Determines if this character is a Kanji character.
     */
    public  boolean isKanji(char c) {
        if (('\u4e00' <= c) && (c <= '\u9fa5')) {
            return true;
        }
        if (('\u3005' <= c) && (c <= '\u3007')) {
            return true;
        }
        return false;
    }

    /**
     * Determines if this character could be used as part of
     * a romaji character.
     */
    public  boolean isRomaji(char c) {
        if (('\u0041' <= c) && (c <= '\u0090'))
            return true;
        else if (('\u0061' <= c) && (c <= '\u007a'))
            return true;
        else if (('\u0021' <= c) && (c <= '\u003a'))
            return true;
        else if (('\u0041' <= c) && (c <= '\u005a'))
            return true;
		else
        return false;
    }

    /**
     * Translates this character into the equivalent Katakana character.
     * The function only operates on Hiragana and always returns the
     * Full width version of the Katakana. If the character is outside the
     * Hiragana then the origianal character is returned.
     */
    public  char toKatakana(char c) {
        if (isHiragana(c)) {
            return (char) (c + 0x60);
        }
        return c;
    }

    /**
     * Translates this character into the equivalent Hiragana character.
     * The function only operates on Katakana characters
     * If the character is outside the Full width or Half width
     * Katakana then the origianal character is returned.
     */
    public  char toHiragana(char c) {
        if (isFullWidthKatakana(c)) {
            return (char) (c - 0x60);
        } else if (isHalfWidthKatakana(c)) {
            return (char) (c - 0xcf25);
        }
        return c;
    }
}
