"""Register four reviewed complete editions; keep source PDF bytes unchanged."""
import hashlib
import json
from pathlib import Path
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'content/books.json'
books = json.loads(path.read_text(encoding='utf-8'))
faq = 'https://d1.islamhouse.com/html/faq.htm'
new = [
    dict(id='tahtawi1290', title='حاشية الطحطاوي على مراقي الفلاح',
         author='أحمد بن محمد الطحطاوي؛ على شرح حسن الشرنبلالي لنور الإيضاح',
         schools=['hanafi'], pages=490,
         description='الكتاب كاملًا في نسخته المصورة القديمة، في فقه الطهارة والصلاة والصيام عند الحنفية، إلى خاتمة المؤلف وخاتمة الطبع. تتضمن النسخة المتن والشرح والحاشية؛ لا تختص بجميع أبواب المعاملات والأسرة. كثافة الطبع القديمة تستلزم التكبير أحيانًا.',
         topics=['الطهارة', 'الصلاة', 'الصيام'],
         chapters=[('الطهارة',13,'13'),('الصلاة',110,'110'),('الصيام',414,'414')],
         publisher='المطبعة الميرية ببولاق، مصر؛ الرقمنة من ويكي مصدر',
         edition='طبعة مصر 1290هـ، وفق صفحة المصدر وخاتمة الطبع في صفحة الملف 490؛ لا يثبت رقم الطبعة',
         source='https://ar.wikisource.org/wiki/ملف:حاشية_أحمد_الطحطاوي_على_مراقي_الفلاح.pdf',
         downloadUrl='https://upload.wikimedia.org/wikisource/ar/b/b9/حاشية_أحمد_الطحطاوي_على_مراقي_الفلاح.pdf',
         licenseUrl='https://ar.wikisource.org/w/index.php?title=ملف:حاشية_أحمد_الطحطاوي_على_مراقي_الفلاح.pdf&oldid=115328',
         sha256='7fa62fba7523272d05f0e7ba1dd3358a7527da338f9faa3786b1ba70dc1c20dd'),
    dict(id='risalah-maliki', title='الرسالة لابن أبي زيد القيرواني',
         author='عبد الله بن أبي زيد القيرواني؛ بمقدمة عبد المجيد الشرنوبي',
         schools=['maliki'], pages=191,
         description='متن الرسالة كاملًا في المذهب المالكي، مع مقدمة النسخة، من العقيدة إلى العبادات والمعاملات والآداب وخاتمة المؤلف. تعرض صفحات المصدر دون اختصار أو إعادة كتابة. بيانات رقم الطبعة وتاريخها غير مثبتة في النسخة المختارة.',
         topics=['الطهارة','الصلاة','الزكاة','الصيام','الحج','المعاملات','الأسرة','الآداب'],
         chapters=[('الطهارة',12,'12'),('الصلاة',27,'27'),('الصيام',69,'69'),('الزكاة',75,'75'),('الحج',84,'84')],
         publisher='المكتبة الثقافية، بيروت — لبنان؛ الرقمنة من ويكي مصدر',
         edition='نسخة المكتبة الثقافية ذات 191 صفحة؛ لا يظهر رقم الطبعة أو تاريخها في الملف أو وصف المصدر، فلا ينسب إليها تاريخ تخميني',
         source='https://ar.wikisource.org/wiki/ملف:متن_الرسالة_لابن_أبي_زيد_القيرواني.pdf',
         downloadUrl='https://upload.wikimedia.org/wikisource/ar/6/67/متن_الرسالة_لابن_أبي_زيد_القيرواني.pdf',
         licenseUrl='https://ar.wikisource.org/w/index.php?title=ملف:متن_الرسالة_لابن_أبي_زيد_القيرواني.pdf&oldid=114605',
         sha256='27f53dc2a654cee192cf95eb22600edded9390565e8ee9087d604f3bb213f863'),
    dict(id='fath-qarib', title='فتح القريب المجيب بشرح متن أبي شجاع',
         author='محمد بن قاسم الغزي؛ وبهامشه متن أبي شجاع أحمد بن الحسين الأصفهاني',
         schools=['shafii'], pages=71,
         description='شرح فتح القريب كاملًا على متن التقريب في المذهب الشافعي، ومعه المتن في الهامش، وفهرس النسخة. من الطهارة إلى العتق وخاتمة الطبع، دون حذف المتن أو الشرح. طبعة قديمة كثيفة السطور؛ تتاح أدوات التكبير لقراءتها.',
         topics=['الطهارة','الصلاة','الزكاة','الصيام','الحج','المعاملات','الأسرة','المواريث','الجنايات','القضاء'],
         chapters=[('الطهارة',4,'3'),('الصلاة',12,'11'),('الزكاة',23,'22'),('الصيام',26,'25'),('الحج',27,'26')],
         publisher='المطبعة الخيرية بمصر؛ الرقمنة من ويكي مصدر',
         edition='طبعة المطبعة الخيرية بمصر 1309هـ، وفق وصف المصدر وخاتمة الطبع بصفحة الملف 71؛ لا يثبت رقم الطبعة',
         source='https://ar.wikisource.org/wiki/ملف:فتح_القريب_المجيب.pdf',
         downloadUrl='https://upload.wikimedia.org/wikisource/ar/9/9d/فتح_القريب_المجيب.pdf',
         licenseUrl='https://ar.wikisource.org/w/index.php?title=ملف:فتح_القريب_المجيب.pdf&oldid=122935',
         sha256='0d1255f19fa30ac44cc5e754bd96d5a5a4760531205ef1bd59713dfeed483d37'),
    dict(id='umdat-fiqh', title='عمدة الفقه',
         author='موفق الدين عبد الله بن أحمد ابن قدامة المقدسي', schools=['hanbali'], pages=160,
         description='متن عمدة الفقه كاملًا على مذهب الإمام أحمد، بنشرة معهد السنة الأولى، مع الفهرس المرفق. يبدأ بالطهارة ويمر بالعبادات والمعاملات والأسرة والقضاء، وينتهي بالإقرار وخاتمة الكتاب ثم الفهرس. تنقل صفحات النسخة دون تغيير أو إعادة تنضيد.',
         topics=['الطهارة','الصلاة','الزكاة','الصيام','الحج','المعاملات','الأسرة','المواريث','الجنايات','القضاء'],
         chapters=[('الطهارة',3,'3'),('الصلاة',12,'12'),('الزكاة',30,'30'),('الصيام',37,'37'),('الحج',41,'41')],
         publisher='معهد السنة — As-Sunnah College؛ المصدر الرقمي دار الإسلام',
         edition='النشرة الأولى 1442هـ؛ سلسلة متون معهد السنة رقم 32، كما في الغلاف؛ أتيحت في فهرس المصدر 29 نوفمبر 2021',
         source='https://islamhouse.com/ar/books/2832689/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single-03/ar-eumdat-alfiqh.pdf',
         licenseName='إذن إعادة نشر مع نسبة المصدر وحفظ المحتوى العلمي', licenseUrl=faq,
         rightsNote='تقرر سياسة دار الإسلام أن محتوياته متاحة للنشر بمختلف الوسائل مع نسبة المصدر ودون تغيير المحتوى العلمي. لم يظهر تحفظ مخالف في النسخة المختارة؛ حفظت كاملة كما نشرها المصدر. لا تصنف هذه النشرة الحديثة ملكية عامة، ولا يستنتج منها ترخيص تجاري مستقل باسم معهد السنة.',
         sha256='bd52eeb4ddb33a72c377f8204fec37b2f95c8c61386254e7ff59cf0ac3890548')
]
for book in new:
    book.update(subject='fiqh', inputFile=book['id']+'.pdf', asset='books/'+book['id']+'.pdf', reviewedOn='2026-10-24')
    book['chapters'] = [dict(title=t,pdfPage=p,printedPage=n) for t,p,n in book['chapters']]
    book.setdefault('licenseName','ملكية عامة بحسب بيان صفحة النسخة المصورة')
    book.setdefault('rightsNote','تصرح صفحة هذه النسخة المصورة في ويكي مصدر بأنها في الملكية العامة لانتهاء مدة حفظ حقوقها. تنسب الرقمنة إلى ويكي مصدر، وتحفظ جميع الصفحات والفهرس والخاتمة دون تغيير. هذا البيان متعلق بالنسخة المحددة، ولا يشمل تلقائيًا أي تحقيق أو طبعة حديثة أخرى للعمل.')
    pdf=ROOT/'content/incoming'/book['inputFile']
    assert hashlib.sha256(pdf.read_bytes()).hexdigest() == book['sha256'], book['id']
    assert len(PdfReader(pdf).pages)==book['pages'], book['id']
    books=[b for b in books if b['id']!=book['id']]
    books.append(book)
path.write_text(json.dumps(books,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Registered four verified editions, 912 added PDF pages, 18 reviewed chapter links.')
