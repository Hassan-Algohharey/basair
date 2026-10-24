"""Admit reviewed v0.4 originals with explicit source and rights provenance."""
import hashlib
import json
from pathlib import Path
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parent.parent
path = ROOT / 'content/books.json'
books = json.loads(path.read_text(encoding='utf-8'))
faq = 'https://d1.islamhouse.com/html/faq.htm'
reviewed_hashes = {
    'four-imams': '06a42a18e684b8d61872c57d4c2b50baf7b11c793fad2191927eac458e682ebc',
    'safwa1': '688107c29d7d8b473857d48ab23f238f58b4faa2e2e5ff5fa613c45562465fd3',
    'safwa2': 'faf826e17d19f2493ceec46436899bea573f72ba963e6e2029b3bc48ba9a9877',
    'wasael': '6623aedcf33306b518d56ae1579dced6a7afaaf64a43046454f82b21a3941b91',
    'risalat': '30d918004f3429c0ca854eae06d6016dce7db40f0fa994f71e79fe8a1a571dd8',
}
new = [
    dict(id='four-imams', title='رجال ومناهج في الفقه الإسلامي — الأئمة الأربعة',
         author='محمد زكي الدين محمد قاسم', subject='salaf', pages=148,
         description='كتاب كامل في سير أبي حنيفة ومالك والشافعي وأحمد ومناهجهم في الفقه، في النسخة الإلكترونية التي نشرها دار الإسلام عن وزارة الأوقاف الكويتية.',
         topics=['الأئمة الأربعة', 'أبو حنيفة', 'النعمان', 'مالك بن أنس', 'الشافعي', 'أحمد بن حنبل', 'المذاهب', 'التراجم'],
         source='https://islamhouse.com/ar/books/381057/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single5/ar_Men_and_approaches_in_the_literature.pdf',
         publisher='المصدر في فهرس دار الإسلام: وزارة الأوقاف والشؤون الإسلامية بالكويت',
         edition='نسخة إلكترونية من 148 صفحة؛ لا يظهر تاريخ طبعة ورقية في النسخة',
         licenseName='إذن إعادة نشر مع نسبة المصدر وحفظ المحتوى — غير تجاري', licenseUrl=faq,
         rightsNote='تعتمد الإتاحة على سياسة دار الإسلام المنشورة في الأسئلة المتكررة. لم تظهر في النسخة الإلكترونية صفحة تحفظ حقوقًا مخالفة لهذا الإذن. لا يُفترض إذن تجاري لجهة النشر الأخرى. الصفحة الأخيرة فارغة في الأصل.'),
    dict(id='safwa1', title='صفة الصفوة — الجزء الأول: الصحابة',
         author='أبو الفرج عبد الرحمن بن علي ابن الجوزي', subject='salaf', pages=348,
         description='المجلد الأول كاملًا كما في النسخة المصورة: يبدأ بالسيرة النبوية، ثم تراجم الصحابة والصحابيات. هذا مجلد من الكتاب، وليس صفة الصفوة بأجزائه جميعًا.',
         topics=['الصحابة', 'الصحابيات', 'أبو بكر', 'عمر بن الخطاب', 'عثمان بن عفان', 'علي بن أبي طالب', 'عائشة', 'الزهد', 'التراجم'],
         source='https://ar.wikisource.org/wiki/ملف:صفة_الصفوة1.pdf',
         downloadUrl='https://upload.wikimedia.org/wikisource/ar/e/e9/صفة_الصفوة1.pdf',
         publisher='دائرة المعارف العثمانية، حيدر آباد الدكن؛ الرقمنة من ويكي مصدر',
         edition='الطبعة الأولى 1355هـ، الجزء الأول؛ يتضمن الفهرس وتصويبات الطبع',
         licenseName='ملكية عامة بحسب صفحة النسخة المصورة',
         licenseUrl='https://ar.wikisource.org/w/index.php?title=ملف:صفة_الصفوة1.pdf&oldid=115074',
         rightsNote='تصنف صفحة الملف هذه النسخة ضمن الملكية العامة القديمة. حُفظت صفحات المجلد والفهرس والتصويبات دون تعديل، مع نسبة الرقمنة إلى ويكي مصدر. لا يُقدَّم هذا المجلد بوصفه الكتاب كاملًا، ولا يعني إدراجه تصحيح كل روايات التراجم.'),
    dict(id='safwa2', title='صفة الصفوة — الجزء الثاني: التابعون ومن بعدهم',
         author='أبو الفرج عبد الرحمن بن علي ابن الجوزي', subject='salaf', pages=316,
         description='المجلد الثاني كاملًا كما في النسخة المصورة؛ يضم تراجم التابعين ومن بعدهم من العلماء والعباد، مرتبة بحسب البلدان. ليس خاصًا بالتابعين وحدهم، وليس الكتاب بجميع أجزائه.',
         topics=['التابعون', 'التابعين', 'سعيد بن المسيب', 'عروة بن الزبير', 'عمر بن عبد العزيز', 'العلماء', 'العباد', 'التراجم'],
         source='https://ar.wikisource.org/wiki/ملف:صفة_الصفوة2.pdf',
         downloadUrl='https://upload.wikimedia.org/wikisource/ar/9/9a/صفة_الصفوة2.pdf',
         publisher='دائرة المعارف العثمانية، حيدر آباد الدكن؛ الرقمنة من ويكي مصدر',
         edition='الطبعة الثانية 1389هـ / 1969م عن طبعة 1355هـ؛ الجزء الثاني',
         licenseName='ملكية عامة بحسب صفحة النسخة المصورة',
         licenseUrl='https://ar.wikisource.org/w/index.php?title=ملف:صفة_الصفوة2.pdf&oldid=115078',
         rightsNote='تصنف صفحة الملف هذه النسخة ضمن الملكية العامة القديمة. حُفظ المجلد دون تعديل، مع نسبة الرقمنة إلى ويكي مصدر. خاتمة الطبع تؤكد أن الجزء الثالث يبدأ بأهل البصرة؛ الأجزاء الأخرى غير مدرجة، ولا يُدّعى اكتمال صفة الصفوة أو صحة جميع رواياته.'),
    dict(id='wasael', title='الوسائل المفيدة للحياة السعيدة',
         author='عبد الرحمن بن ناصر السعدي', subject='tazkiyah', pages=23,
         description='الرسالة كاملة في أسباب طمأنينة القلب، والإيمان والعمل الصالح والذكر والتوكل. النسخة ذات إذن النشر المطبوع داخلها، مع فهرسها الأصلي.',
         topics=['التزكية', 'تزكية النفس', 'الطمأنينة', 'السعادة', 'التوكل', 'الصبر', 'الذكر', 'الأخلاق'],
         source='https://islamhouse.com/ar/books/2816219/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single_01/ar-alwasael-almofedah-llhyah-alsa3edah.pdf',
         publisher='دار الإسلام، جمعية الربوة وشركاء التنفيذ المذكورون في صفحة الإتاحة',
         edition='النسخة الإلكترونية المرفقة الثانية في فهرس المصدر، بتاريخ 24 يوليو 2022',
         licenseName='إذن نشر صريح مع نسبة المصدر وعدم تغيير النص', licenseUrl='https://islamhouse.com/ar/books/2816219/',
         rightsNote='في صفحة الملف 2 إذن صريح بطباعة الإصدار ونشره بأي وسيلة، مع الإشارة إلى المصدر وعدم التغيير في النص. حُفظت النسخة الأصلية دون تعديل. لا يستنتج التطبيق من ذلك ترخيصًا تجاريًا أوسع من سياسة جهة المصدر.'),
    dict(id='risalat', title='رسالة ابن القيم إلى أحد إخوانه',
         author='محمد بن أبي بكر ابن قيم الجوزية؛ تحقيق عبد الله بن محمد المديفر', subject='tazkiyah', pages=103,
         description='الرسالة كاملة في النصيحة، وحفظ الوقت وصلاح القلب والإخلاص والمتابعة والإحسان، مع مقدمة التحقيق والفهارس كما نشرها المصدر.',
         topics=['التزكية', 'تزكية النفس', 'الإخلاص', 'صلاح القلب', 'النصيحة', 'الإحسان', 'الوقت', 'ابن القيم'],
         source='https://islamhouse.com/ar/books/265607/',
         downloadUrl='https://d1.islamhouse.com/data/ar/ih_books/single-03/ar-Risalat-ibn-Alqayyim.pdf',
         publisher='عطاءات العلم؛ على غلاف النسخة دار عالم الفوائد ودار ابن حزم',
         edition='النسخة الإلكترونية ذات 103 صفحات المرفقة بالمصدر بتاريخ 8 مايو 2021',
         licenseName='إذن إعادة نشر مع نسبة المصدر وحفظ المحتوى — غير تجاري', licenseUrl=faq,
         rightsNote='تعتمد الإتاحة على سياسة دار الإسلام لإعادة نشر محتوى الموقع مع نسبة المصدر وعدم تغيير المحتوى العلمي؛ ليست ادعاءً بأن تحقيق الناشر الحديث ملكية عامة. لا يُفترض إذن استغلال تجاري لمواد الجهات الأخرى. حفظت النسخة كاملة دون تعديل.'),
]
for book in new:
    data = (ROOT / 'content/incoming' / f'{book["id"]}.pdf').read_bytes()
    assert hashlib.sha256(data).hexdigest() == reviewed_hashes[book['id']], 'Original changed; review is required.'
    assert len(PdfReader(ROOT / 'content/incoming' / f'{book["id"]}.pdf').pages) == book['pages']
    book.update(inputFile=f'{book["id"]}.pdf', asset=f'books/{book["id"]}.pdf', sha256=hashlib.sha256(data).hexdigest())
    books = [b for b in books if b['id'] != book['id']]
    books.append(book)
path.write_text(json.dumps(books, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
